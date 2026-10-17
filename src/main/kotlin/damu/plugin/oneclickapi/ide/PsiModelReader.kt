package damu.plugin.oneclickapi.ide

import damu.plugin.oneclickapi.core.model.EntityModel
import damu.plugin.oneclickapi.core.model.FieldModel
import damu.plugin.oneclickapi.core.model.OrmType
import damu.plugin.oneclickapi.core.model.RelationKind
import damu.plugin.oneclickapi.core.model.RelationTarget
import damu.plugin.oneclickapi.core.model.boxed
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiEnumConstant
import com.intellij.psi.PsiField
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeParameter
import com.intellij.psi.PsiWildcardType

/** The only place that turns PSI into the plain model the generator works on. Call inside a read action. */
object PsiModelReader {

    private val PERSISTENCE = setOf("jakarta.persistence", "javax.persistence")
    private val VALIDATION = listOf("jakarta.validation.constraints.", "javax.validation.constraints.")
    private val NOT_NULL = setOf("NotNull", "NotBlank", "NotEmpty", "NonNull")

    fun read(psiClass: PsiClass, moduleDir: VirtualFile?): EntityModel? {
        val name = psiClass.name ?: return null
        val entity = psiClass.annotations.firstOrNull {
            it.qualifiedName == "jakarta.persistence.Entity" || it.qualifiedName == "javax.persistence.Entity"
        }
        val orm = if (entity != null) OrmType.JPA else OrmType.MYBATIS

        val fields = psiClass.allFields
            .filter { !it.hasModifierProperty(PsiModifier.STATIC) && !it.hasModifierProperty(PsiModifier.TRANSIENT) }
            .filter { orm != OrmType.JPA || it.persistence("Transient") == null }
            .map(::readField)

        val sourcePath = psiClass.containingFile?.virtualFile?.let { vf ->
            moduleDir?.let { VfsUtilCore.getRelativePath(vf, it) }
        }
        val lombok = psiClass.annotations.mapNotNull { it.qualifiedName }.toSet()
        return EntityModel(
            name = name,
            packageName = (psiClass.containingFile as? PsiJavaFile)?.packageName ?: "",
            orm = orm,
            fields = fields,
            sourcePath = sourcePath,
            tableName = psiClass.persistence("Table")?.str("name"),
            lombokAccessors = "lombok.Data" in lombok || ("lombok.Getter" in lombok && "lombok.Setter" in lombok),
            jakartaPersistence = entity?.qualifiedName?.startsWith("jakarta.")
        )
    }

    private fun readField(field: PsiField): FieldModel {
        val type = field.type
        val typeText = type.presentableText
        val primitive = type is com.intellij.psi.PsiPrimitiveType

        val relation = when {
            field.persistence("ManyToOne") != null -> RelationKind.MANY_TO_ONE
            field.persistence("OneToOne") != null -> RelationKind.ONE_TO_ONE
            field.persistence("OneToMany") != null -> RelationKind.ONE_TO_MANY
            field.persistence("ManyToMany") != null -> RelationKind.MANY_TO_MANY
            else -> RelationKind.NONE
        }
        val relationAnn = field.persistence("ManyToOne", "OneToOne", "OneToMany", "ManyToMany")
        val targetClass: PsiClass? = when (relation) {
            RelationKind.MANY_TO_ONE, RelationKind.ONE_TO_ONE -> (type as? PsiClassType)?.resolve()
            RelationKind.ONE_TO_MANY, RelationKind.MANY_TO_MANY ->
                (type as? PsiClassType)?.parameters?.firstOrNull()?.let { (it as? PsiClassType)?.resolve() }
            RelationKind.NONE -> null
        }
        val target = targetClass?.let { c ->
            val (idName, idType) = idOf(c)
            RelationTarget(c.name ?: return@let null, c.qualifiedName ?: return@let null, idName, idType)
        }

        val column = field.persistence("Column")
        val joinColumn = field.persistence("JoinColumn")
        val notNullAnnotation = field.annotations.any { a -> a.qualifiedName?.substringAfterLast('.') in NOT_NULL }
        val nullable = !(
                primitive || notNullAnnotation ||
                        field.persistence("Id", "EmbeddedId") != null ||
                        column?.bool("nullable") == false ||
                        joinColumn?.bool("nullable") == false ||
                        field.persistence("Basic")?.bool("optional") == false ||
                        relationAnn?.bool("optional") == false
                )

        val validation = field.annotations.filter { a -> VALIDATION.any { a.qualifiedName?.startsWith(it) == true } }

        val enumClass = (type as? PsiClassType)?.resolve()?.takeIf { it.isEnum }
        val constants = enumClass?.fields?.filterIsInstance<PsiEnumConstant>()?.mapNotNull { it.name } ?: emptyList()

        val imports = sortedSetOf<String>()
        collectImports(type, imports)

        return FieldModel(
            name = field.name,
            type = typeText,
            imports = imports,
            isId = field.persistence("Id", "EmbeddedId") != null,
            nullable = nullable,
            length = column?.int("length"),
            columnName = column?.str("name") ?: joinColumn?.str("name"),
            validationAnnotations = validation.map { it.text },
            validationImports = validation.mapNotNull { it.qualifiedName }.toSet(),
            relation = relation,
            relationTarget = target,
            inverse = relationAnn?.findDeclaredAttributeValue("mappedBy") != null,
            isEnum = enumClass != null,
            enumFqn = enumClass?.qualifiedName,
            enumFirstConstant = constants.firstOrNull(),
            enumConstants = constants
        )
    }

    private fun idOf(c: PsiClass): Pair<String, String> {
        val f = c.allFields.firstOrNull { it.persistence("Id", "EmbeddedId") != null }
            ?: c.allFields.firstOrNull { it.name.equals("id", ignoreCase = true) }
        return (f?.name ?: "id") to boxed(f?.type?.presentableText ?: "Long")
    }

    private fun collectImports(type: PsiType, out: MutableSet<String>) {
        when (type) {
            is PsiArrayType -> collectImports(type.componentType, out)
            is PsiWildcardType -> type.bound?.let { collectImports(it, out) }
            is PsiClassType -> {
                val cls = type.resolve()
                val q = cls?.qualifiedName
                val isJavaLang = q != null && q.startsWith("java.lang.") && q.count { it == '.' } == 2
                if (cls != null && cls !is PsiTypeParameter && q != null && !isJavaLang) out += q
                type.parameters.forEach { collectImports(it, out) }
            }
        }
    }

    // ---------------------------------------------------------------------------- annotation helpers

    /** A JPA annotation (either namespace) on this element whose simple name is one of [names]. */
    private fun PsiModifierListOwner.persistence(vararg names: String): PsiAnnotation? =
        annotations.firstOrNull { a ->
            val q = a.qualifiedName ?: return@firstOrNull false
            q.substringBeforeLast('.') in PERSISTENCE && q.substringAfterLast('.') in names
        }

    private fun PsiAnnotation.int(name: String): Int? = (findDeclaredAttributeValue(name) as? PsiLiteralExpression)?.value as? Int
    private fun PsiAnnotation.str(name: String): String? = (findDeclaredAttributeValue(name) as? PsiLiteralExpression)?.value as? String
    private fun PsiAnnotation.bool(name: String): Boolean? = (findDeclaredAttributeValue(name) as? PsiLiteralExpression)?.value as? Boolean
}
