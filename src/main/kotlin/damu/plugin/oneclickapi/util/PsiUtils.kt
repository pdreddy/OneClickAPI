package damu.plugin.oneclickapi.util

import com.intellij.psi.PsiClass

// Utility object for working with PsiClass objects
object PsiUtils {

    // Function to check if a given PsiClass is an Entity class
    fun isEntityClass(psiClass: PsiClass): Boolean {
        val entityAnnotations = listOf("jakarta.persistence.Entity", "javax.persistence.Entity")
        return psiClass.annotations.any { it.qualifiedName in entityAnnotations }
    }


    // Function to check if a given PsiClass is a POJO class (MyBatis)
//    fun isPojoClass2(psiClass: PsiClass): Boolean {
//        // Check for default constructor
//        val hasDefaultConstructor = psiClass.constructors.any { it.parameterList.parameters.isEmpty() }
//
//        // Check for getters and setters
//        val fields = psiClass.fields.map { it.name }
//        val methods = psiClass.methods.map { it.name }
//        val hasGettersAndSetters = fields.all { field ->
//            methods.contains("get${field.capitalize()}") && methods.contains("set${field.capitalize()}")
//        }
//
//        // Ensure the class is not annotated with @Entity
//        val isNotEntity = !isEntityClass(psiClass)
//
//        return hasDefaultConstructor && hasGettersAndSetters && isNotEntity
//    }

//    fun isPojoClassThird(psiClass: PsiClass): Boolean {
//        // POJO class should be a regular Java class with fields
//        // Not an Entity, not an interface, not abstract, not enum
//        if (isEntityClass(psiClass)) return false
//        if (psiClass.isInterface) return false
//        if (psiClass.isEnum) return false
//        if (psiClass.hasModifierProperty("abstract")) return false
//
//        // Should have at least one field
//        val hasFields = psiClass.fields.isNotEmpty()
//
//        return hasFields
//    }




    fun isPojoClass(psiClass: PsiClass): Boolean {

        // 1. It should not be entity, interface, enum, abstract
        if (isEntityClass(psiClass)) return false
        if (psiClass.isInterface) return false
        if (psiClass.isEnum) return false
        if (psiClass.hasModifierProperty("abstract")) return false

        // 2. Must be a field
        val fields = psiClass.fields
        if (fields.isEmpty()) return false

        // 3. Lombok-pojo annotation check
        val lombokAnnotations = listOf(
            "Data", "Getter", "Setter", "Value",
            "NoArgsConstructor", "AllArgsConstructor"
        )

        val hasLombok = psiClass.annotations.any { ann ->
            lombokAnnotations.contains(ann.qualifiedName?.substringAfterLast("."))
        }

        // 4. Physical getter/setter check
        val methodNames = psiClass.methods.map { it.name }

        val hasPhysicalGettersSetters = fields.all { field ->
            val cap = field.name.replaceFirstChar { it.uppercase() }
            methodNames.contains("get$cap") && methodNames.contains("set$cap")
        }

        // 5. Default constructor
        val hasDefaultConstructor = psiClass.constructors.any { it.parameterList.parametersCount == 0 }

        // 6. Whichever condition is sufficient to count POJOs, we return true
        return hasPhysicalGettersSetters || hasLombok || hasDefaultConstructor
    }




    // Helper function to capitalize the first letter of a string
    private fun String.capitalize(): String {
        if (this.isEmpty()) return this
        return this.substring(0, 1).lowercase() + this.substring(1)
    }
}