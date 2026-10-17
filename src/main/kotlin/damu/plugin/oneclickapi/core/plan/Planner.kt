package damu.plugin.oneclickapi.core.plan

import damu.plugin.oneclickapi.core.model.EntityModel
import damu.plugin.oneclickapi.core.model.FieldModel
import damu.plugin.oneclickapi.core.model.OrmType
import damu.plugin.oneclickapi.core.model.RelationKind
import damu.plugin.oneclickapi.core.model.capFirst
import damu.plugin.oneclickapi.core.model.decapFirst
import damu.plugin.oneclickapi.core.options.ApiVersioning
import damu.plugin.oneclickapi.core.options.ConflictPolicy
import damu.plugin.oneclickapi.core.options.ConverterType
import damu.plugin.oneclickapi.core.options.GenerationOptions
import damu.plugin.oneclickapi.core.project.ProjectProfile
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Turns entities + options into a [Plan]: the complete list of files that generating would write, with their
 * content. Pure: no PSI, no disk access except the injected [exists] probe (used to pick a free Liquibase
 * file name).
 */
class Planner(
    private val profile: ProjectProfile,
    private val templates: TemplateEngine = TemplateEngine(),
    private val exists: (String) -> Boolean = { false },
    private val clock: () -> LocalDateTime = { LocalDateTime.now() }
) {

    fun plan(entities: List<EntityModel>, o: GenerationOptions): Plan {
        val files = LinkedHashMap<String, PlannedFile>()
        val warnings = mutableListOf<String>()
        fun add(f: PlannedFile) {
            files.putIfAbsent(f.path, f)
        }

        val std = o.standards
        val needsHandler = o.exceptionHandling || std.standardResponse

        for ((index, entity) in entities.withIndex()) {
            val ctx = EntityContext(entity, o, profile)
            warnings += ctx.warnings
            if (o.crud) {
                crud(ctx).forEach(::add)
                if (entity.orm == OrmType.MYBATIS) {
                    warnings += "MyBatis: mapper XML is written to src/main/resources/mybatis/mappers/. " +
                            "Make sure application.properties has mybatis.mapper-locations=classpath:mybatis/mappers/*.xml " +
                            "(and mybatis.configuration.map-underscore-to-camel-case=true if your columns are snake_case)."
                }
            }
            if (o.liquibase != null) add(liquibase(ctx, o, index, entities.size))
        }

        if (o.crud && entities.isNotEmpty()) {
            val any = EntityContext(entities.first(), o, profile)
            if (std.pagination) add(source(any.dtoRootPkg, "PageResponse", templates.render("PageResponse.java.ft", mapOf("classPackageName" to any.dtoRootPkg)), "Page envelope for list endpoints"))
            if (std.standardResponse) {
                add(source(any.dtoRootPkg, "ApiResult", templates.render("ApiResult.java.ft", mapOf("classPackageName" to any.dtoRootPkg)), "Success response envelope"))
                add(source(any.dtoRootPkg, "ApiError", templates.render("ApiError.java.ft", mapOf("classPackageName" to any.dtoRootPkg)), "Error response body"))
                add(source(any.pkg(o.layout.exception), "ResourceNotFoundException", templates.render("ResourceNotFoundException.java.ft", mapOf("classPackageName" to any.pkg(o.layout.exception))), "404 exception"))
            }
            if (o.openApi.config) add(openApiConfig(any, o))
        }

        if (needsHandler && entities.isNotEmpty()) add(exceptionHandler(EntityContext(entities.first(), o, profile), o))
        else if (needsHandler) warnings += "Exception handling was requested but no class is selected to take the package from."

        if (o.tests.httpFile && files.keys.any { it.startsWith("http-requests/") }) {
            add(
                PlannedFile(
                    "http-requests/http-client.env.json",
                    "{\n  \"dev\": {\n    \"host\": \"http://localhost:8080\"\n  }\n}\n",
                    FileKind.HTTP, "Environment for the .http files (never overwritten)", "shared", ConflictPolicy.SKIP
                )
            )
        }

        if (o.imageUpload || o.pdfUpload) {
            if (entities.isEmpty()) warnings += "File upload APIs need a class to take the package from."
            else uploads(EntityContext(entities.first(), o, profile), o).forEach(::add)
        }

        return Plan(files.values.toList(), warnings.distinct())
    }

    // ------------------------------------------------------------------------------------------ helpers

    private fun javaPath(pkg: String, name: String, test: Boolean = false): String =
        (if (test) "src/test/java/" else "src/main/java/") + (if (pkg.isEmpty()) "" else pkg.replace('.', '/') + "/") + "$name.java"

    private fun source(pkg: String, name: String, content: String, description: String, group: String = "shared", test: Boolean = false) =
        PlannedFile(javaPath(pkg, name, test), content, if (test) FileKind.TEST else FileKind.SOURCE, description, group)

    private fun pkgModel(c: EntityContext) = mapOf(
        "controller" to c.pkg(c.o.layout.controller),
        "service" to c.pkg(c.o.layout.service),
        "repository" to c.pkg(c.o.layout.repository),
        "mapper" to c.pkg(c.o.layout.mapper),
        "persistence" to c.pkg(c.o.layout.persistence),
        "exception" to c.pkg(c.o.layout.exception),
        "config" to c.pkg(c.o.layout.config),
        "dtoRoot" to c.dtoRootPkg,
        "dtoReq" to c.requestPkg,
        "dtoResp" to c.responsePkg
    )

    private fun baseModel(c: EntityContext): MutableMap<String, Any?> = mutableMapOf(
        "className" to c.name,
        "varName" to c.name.decapFirst(),
        "idType" to c.entity.idType,
        "idName" to c.entity.idName,
        "idGetter" to c.entity.idGetter,
        "entityFqn" to c.entity.fqn,
        "lombok" to c.o.lombok,
        "jakarta" to c.jakarta,
        "boot3" to (profile.bootMajor >= 3),
        "persistencePkg" to c.persistencePkg,
        "validationPkg" to c.validationPkg,
        "pkg" to pkgModel(c),
        "pagination" to c.std.pagination,
        "wrap" to c.std.standardResponse,
        "soft" to c.softDelete,
        "audit" to c.audit,
        "openApi" to c.o.openApi.annotations,
        "basePath" to c.basePath,
        "versionHeader" to c.versionHeader,
        "apiVersion" to c.std.apiVersion,
        "customRegion" to CUSTOM_REGION,
        "mapperName" to c.dtoMapperName,
        "dtoMapperName" to c.dtoMapperName
    )

    // ------------------------------------------------------------------------------------------ CRUD

    private fun crud(c: EntityContext): List<PlannedFile> {
        val out = mutableListOf<PlannedFile>()
        val o = c.o
        val n = c.name
        val g = c.name
        out += dto(c, "Request")
        out += dto(c, "Response")
        if (c.std.pagination) out += filter(c)

        // converter
        out += if (o.converter == ConverterType.MANUAL) manualMapper(c) else mapStructMapper(c)

        if (c.isJpa) {
            val repoPkg = c.pkg(o.layout.repository)
            out += source(
                repoPkg, "${n}Repository",
                templates.render(
                    "Repository.java.ft",
                    baseModel(c) + mapOf(
                        "classPackageName" to repoPkg,
                        "repositoryImport" to o.repositoryType.fqn,
                        "repositoryType" to o.repositoryType.simpleName
                    )
                ),
                "Spring Data repository", g
            )
        } else {
            out += myBatisMapper(c)
        }

        out += service(c)
        out += controller(c)
        entityPatch(c)?.let { out += it }
        if (o.tests.serviceTests) out += serviceTest(c)
        if (o.tests.controllerTests) out += controllerTest(c)
        if (o.tests.httpFile) out += http(c)
        if (o.openApi.spec) out += openApiSpec(c)
        return out
    }

    // ------------------------------------------------------------------------------------------ DTOs

    private fun dto(c: EntityContext, kind: String): PlannedFile {
        val request = kind == "Request"
        val pkg = if (request) c.requestPkg else c.responsePkg
        val fields = if (request) c.requestFields else c.responseFields
        val imports = sortedSetOf<String>()
        fields.forEach { imports += it.imports }
        if (c.o.lombok) imports += listOf("lombok.AllArgsConstructor", "lombok.Builder", "lombok.Data", "lombok.NoArgsConstructor")
        if (c.o.openApi.annotations) imports += "io.swagger.v3.oas.annotations.media.Schema"
        val model = baseModel(c) + mapOf(
            "classPackageName" to pkg,
            "dtoClass" to "${c.name}$kind",
            "description" to (if (request) "Payload to create or update a ${c.name}" else "A ${c.name} as returned by the API"),
            "imports" to cleanImports(imports, pkg),
            "fields" to fields.map { f ->
                mapOf(
                    "name" to f.name, "type" to f.type, "annotations" to f.annotations,
                    "getter" to f.getter, "setter" to f.setter,
                    "example" to f.sample?.text?.let { Samples.javaString(it) }
                )
            }
        )
        return source(pkg, "${c.name}$kind", templates.render("Dto.java.ft", model), "$kind DTO", c.name)
    }

    private fun filter(c: EntityContext): PlannedFile {
        val pkg = c.requestPkg
        val imports = sortedSetOf<String>()
        c.filterFields.forEach { imports += it.imports }
        if (c.o.lombok) imports += listOf("lombok.Data", "lombok.NoArgsConstructor")
        val model = baseModel(c) + mapOf(
            "classPackageName" to pkg,
            "imports" to cleanImports(imports, pkg),
            "searchable" to c.searchable.map { it.name },
            "fields" to c.filterFields.map { mapOf("name" to it.name, "type" to it.type, "getter" to it.getter, "setter" to it.setter) }
        )
        return source(pkg, "${c.name}Filter", templates.render("Filter.java.ft", model), "Query parameters of the list endpoint", c.name)
    }

    private fun cleanImports(imports: Set<String>, ownPackage: String): List<String> =
        imports.filter { it.contains('.') && !it.startsWith("java.lang.") || it.startsWith("java.lang.") && it.count { ch -> ch == '.' } > 2 }
            .filter { it.substringBeforeLast('.') != ownPackage }
            .sorted()

    // ------------------------------------------------------------------------------------------ mapper

    private fun targetIdGetter(f: FieldModel) = "get" + (f.relationTarget?.idName ?: "id").capFirst()

    private fun mapStructMapper(c: EntityContext): PlannedFile {
        val pkg = c.pkg(c.o.layout.mapper)
        val response = mutableListOf<String>()
        for (df in c.responseFields) {
            when (df.kind) {
                DtoKind.REL_ID -> response += "@Mapping(source = \"${df.source.name}.${df.source.relationTarget!!.idName}\", target = \"${df.name}\")"
                DtoKind.REL_IDS -> {
                    val g = df.source.getter
                    response += "@Mapping(target = \"${df.name}\", expression = \"java(entity.$g() == null ? null : entity.$g().stream()" +
                            ".map(e -> e.${targetIdGetter(df.source)}()).collect(java.util.stream.Collectors.toList()))\")"
                }

                DtoKind.PLAIN -> Unit
            }
        }
        val request = c.ignoredOnRequest.map { "@Mapping(target = \"${it.name}\", ignore = true)" }
        val model = baseModel(c) + mapOf(
            "classPackageName" to pkg,
            "responseMappings" to response,
            "requestMappings" to request,
            "hasMappings" to (response.isNotEmpty() || request.isNotEmpty())
        )
        return source(pkg, c.dtoMapperName, templates.render("MapperMapStruct.java.ft", model), "MapStruct mapper", c.name)
    }

    private fun manualMapper(c: EntityContext): PlannedFile {
        val pkg = c.pkg(c.o.layout.mapper)
        var collectors = false
        val response = mutableListOf<String>()
        for (df in c.responseFields) {
            val g = df.source.getter
            response += when (df.kind) {
                DtoKind.PLAIN -> "response.${df.setter}(entity.$g());"
                DtoKind.REL_ID -> "response.${df.setter}(entity.$g() == null ? null : entity.$g().${targetIdGetter(df.source)}());"
                DtoKind.REL_IDS -> {
                    collectors = true
                    "response.${df.setter}(entity.$g() == null ? null : entity.$g().stream()" +
                            ".map(e -> e.${targetIdGetter(df.source)}()).collect(Collectors.toList()));"
                }
            }
        }
        val request = c.requestFields.filter { it.kind == DtoKind.PLAIN }
            .map { "entity.${it.source.setter}(dto.${it.getter}());" }
        val model = baseModel(c) + mapOf(
            "classPackageName" to pkg,
            "responseAssignments" to response,
            "requestAssignments" to request,
            "usesCollectors" to collectors
        )
        return source(pkg, c.dtoMapperName, templates.render("MapperManual.java.ft", model), "Manual mapper", c.name)
    }

    // ------------------------------------------------------------------------------------------ service / controller

    private fun service(c: EntityContext): PlannedFile {
        val pkg = c.pkg(c.o.layout.service)
        val model = baseModel(c) + mapOf(
            "classPackageName" to pkg,
            "sortable" to c.sortable.map { it.first },
            "sortColumns" to c.sortable.map { mapOf("property" to it.first, "column" to it.second) },
            "searchable" to c.searchable.map { mapOf("name" to it.name) },
            "filterFields" to c.filterFields.map {
                mapOf(
                    "name" to it.name, "getter" to it.getter, "mode" to it.mode,
                    "relationProperty" to it.relationProperty, "relationIdName" to it.relationIdName
                )
            },
            "relations" to c.relations.map {
                mapOf("setter" to it.source.setter, "dtoGetter" to it.dto.getter, "target" to it.targetSimple)
            },
            "relationImports" to c.relations.map { it.targetFqn }.distinct().sorted(),
            "deletedPrimitive" to c.deletedPrimitive,
            "deletedGetter" to c.deletedGetter,
            "createdSetter" to (if (c.o.standards.auditFields) c.fields.firstOrNull { it.name == "createdAt" }?.setter else null),
            "updatedSetter" to (if (c.o.standards.auditFields) c.fields.firstOrNull { it.name == "updatedAt" }?.setter else null)
        )
        val template = if (c.isJpa) "Service.java.ft" else "MyBatisService.java.ft"
        return source(pkg, "${c.name}Service", templates.render(template, model), "Service", c.name)
    }

    private fun controller(c: EntityContext): PlannedFile {
        val pkg = c.pkg(c.o.layout.controller)
        val model = baseModel(c) + mapOf(
            "classPackageName" to pkg,
            "parameterObject" to (c.o.openApi.annotations && profile.bootMajor >= 3)
        )
        return source(pkg, "${c.name}Controller", templates.render("Controller.java.ft", model), "REST controller", c.name)
    }

    // ------------------------------------------------------------------------------------------ MyBatis

    private fun myBatisMapper(c: EntityContext): List<PlannedFile> {
        val pkg = c.pkg(c.o.layout.persistence)
        val model = baseModel(c) + mapOf("classPackageName" to pkg)
        val iface = source(pkg, "${c.name}Mapper", templates.render("MyBatisMapper.java.ft", model), "MyBatis mapper interface", c.name)

        val persisted = c.fields.filter { it.relation == RelationKind.NONE && !it.isCollection }
        fun col(f: FieldModel) = f.columnName ?: f.name
        val generatedKey = c.usesIdGeneratedKey()
        val idField = c.entity.idField
        val insertFields = persisted.filter { generatedKey && it.name == idField?.name }.let { persisted - it.toSet() }
        val xmlModel = model + mapOf(
            "table" to c.table,
            "idColumn" to (idField?.let(::col) ?: "id"),
            "generatedKey" to generatedKey,
            "columns" to persisted.map { mapOf("property" to it.name, "column" to col(it), "isId" to (it.name == idField?.name)) },
            "insertColumns" to insertFields.joinToString(", ") { col(it) },
            "insertValues" to insertFields.joinToString(", ") { "#{${it.name}}" },
            "updateAssignments" to persisted.filter { it.name != idField?.name }.joinToString(", ") { "${col(it)} = #{${it.name}}" },
            "searchable" to c.searchable.map { mapOf("column" to it.column) },
            "filterFields" to c.filterFields.map { mapOf("name" to it.name, "column" to it.column, "mode" to it.mode) }
        )
        val xml = PlannedFile(
            "src/main/resources/mybatis/mappers/${c.name}Mapper.xml",
            templates.render("MyBatisMapper.xml.ft", xmlModel),
            FileKind.RESOURCE, "MyBatis mapper XML", c.name
        )
        return listOf(iface, xml)
    }

    // ------------------------------------------------------------------------------------------ entity patch

    private fun entityPatch(c: EntityContext): PlannedFile? {
        if (c.patchFields.isEmpty()) return null
        val e = c.entity
        val imports = sortedSetOf<String>()
        val body = StringBuilder()
        for (f in c.patchFields) {
            imports += f.imports
            if (c.isJpa) {
                when (f.name) {
                    "createdAt" -> {
                        imports += "org.hibernate.annotations.CreationTimestamp"; imports += "${c.persistencePkg}.Column"
                        body.append("    @CreationTimestamp\n    @Column(name = \"created_at\", updatable = false)\n")
                    }

                    "updatedAt" -> {
                        imports += "org.hibernate.annotations.UpdateTimestamp"; imports += "${c.persistencePkg}.Column"
                        body.append("    @UpdateTimestamp\n    @Column(name = \"updated_at\")\n")
                    }

                    "deleted" -> {
                        imports += "${c.persistencePkg}.Column"
                        body.append("    @Column(name = \"deleted\", nullable = false)\n")
                    }
                }
            }
            body.append("    private ${f.type} ${f.name}${if (f.name == "deleted") " = false" else ""};\n\n")
        }
        if (!e.lombokAccessors) {
            for (f in c.patchFields) {
                body.append("    public ${f.type} ${f.getter}() {\n        return ${f.name};\n    }\n\n")
                body.append("    public void ${f.setter}(${f.type} ${f.name}) {\n        this.${f.name} = ${f.name};\n    }\n\n")
            }
        }
        val text = buildString {
            if (e.packageName.isNotEmpty()) append("package ${e.packageName};\n\n")
            imports.forEach { append("import $it;\n") }
            append("\npublic class ${e.name} {\n\n")
            append(body.toString().trimEnd('\n'))
            append("\n}\n")
        }
        val what = c.patchFields.joinToString(", ") { it.name }
        return PlannedFile(e.sourcePath!!, text, FileKind.ENTITY_PATCH, "Adds $what to ${e.name}", e.name, ConflictPolicy.MERGE)
    }

    // ------------------------------------------------------------------------------------------ tests / http / openapi

    private fun idSample(c: EntityContext): Sample =
        Samples.of(c.entity.idType, c.entity.idName) ?: Sample("null", "null", "null", "string")

    private fun requestJson(c: EntityContext, pretty: Boolean): String {
        val pairs = c.requestFields.mapNotNull { f -> f.sample?.let { f.name to it.json } }
        if (pairs.isEmpty()) return "{}"
        return if (pretty) pairs.joinToString(",\n", "{\n", "\n}") { "  \"${it.first}\": ${it.second}" }
        else pairs.joinToString(",", "{", "}") { "\"${it.first}\":${it.second}" }
    }

    private fun testModel(c: EntityContext): MutableMap<String, Any?> {
        val id = idSample(c)
        val entityDeleted = if (c.deletedPrimitive) "entity.isDeleted()" else "Boolean.TRUE.equals(entity.getDeleted())"
        val itemPath = if (c.std.standardResponse) "$.data." else "$."
        val listPath = when {
            c.std.pagination -> itemPath + "content[0]."
            c.std.standardResponse -> "$.data[0]."
            else -> "$[0]."
        }
        return (baseModel(c) + mapOf(
            "jpa" to c.isJpa,
            "idSample" to id.java,
            "idJson" to id.json,
            "entityDeleted" to entityDeleted,
            "relations" to c.relations.map { mapOf("target" to it.targetSimple) },
            "itemPath" to itemPath,
            "listPath" to listPath,
            "requestJson" to Samples.javaString(requestJson(c, pretty = false)),
            "hasRequiredFields" to c.requestFields.any { f -> f.annotations.any { a -> REQUIRED.any { a.startsWith("@$it") } } },
            "responseSetters" to c.responseFields.filter { it.sample != null }.map { "response.${it.setter}(${it.sample!!.java});" }
        )).toMutableMap()
    }

    private fun serviceTest(c: EntityContext): PlannedFile {
        val pkg = c.pkg(c.o.layout.service)
        val model = testModel(c) + mapOf("classPackageName" to pkg)
        return source(pkg, "${c.name}ServiceTest", templates.render("ServiceTest.java.ft", model), "JUnit 5 + Mockito service test", c.name, test = true)
    }

    private fun controllerTest(c: EntityContext): PlannedFile {
        val pkg = c.pkg(c.o.layout.controller)
        val model = testModel(c) + mapOf("classPackageName" to pkg)
        return source(pkg, "${c.name}ControllerTest", templates.render("ControllerTest.java.ft", model), "JUnit 5 + MockMvc controller test", c.name, test = true)
    }

    private fun http(c: EntityContext): PlannedFile {
        val model = baseModel(c) + mapOf(
            "requestJson" to requestJson(c, pretty = true),
            "dataAccess" to if (c.std.standardResponse) ".data" else ""
        )
        return PlannedFile(
            "http-requests/${c.name}.http", templates.render("Http.http.ft", model), FileKind.HTTP,
            "IntelliJ HTTP Client requests", c.name
        )
    }

    private fun apiTitle(c: EntityContext): String =
        (c.o.basePackage.substringAfterLast('.').ifEmpty { "Application" }).capFirst() + " API"

    private fun openApiSpec(c: EntityContext): PlannedFile {
        fun props(fields: List<DtoField>) = fields.map { f ->
            val lines = mutableListOf<String>()
            val s = f.sample
            when {
                f.kind == DtoKind.REL_IDS -> {
                    lines += "type: array"
                    val idSample = Samples.of(f.source.relationTarget!!.idType)
                    lines += "items: { type: ${idSample?.openApiType ?: "integer"}${idSample?.openApiFormat?.let { ", format: $it" } ?: ""} }"
                }

                f.source.isEnum && f.kind == DtoKind.PLAIN -> {
                    lines += "type: string"
                    if (f.source.enumConstants.isNotEmpty()) lines += "enum: [${f.source.enumConstants.joinToString(", ")}]"
                }

                s != null -> {
                    lines += "type: ${s.openApiType}"
                    s.openApiFormat?.let { lines += "format: $it" }
                    lines += "example: " + if (s.openApiType in setOf("integer", "number", "boolean")) s.text else "\"${s.text}\""
                }

                else -> lines += "type: object"
            }
            mapOf("name" to f.name, "lines" to lines)
        }

        val id = idSample(c)
        val wrap = c.std.standardResponse
        val pagination = c.std.pagination
        val listRef = when {
            wrap -> if (pagination) "ApiResultPage${c.name}Response" else "ApiResult${c.name}ListResponse"
            else -> if (pagination) "Page${c.name}Response" else null
        }
        val model = baseModel(c) + mapOf(
            "title" to apiTitle(c),
            "version" to "1.0.0",
            "sortable" to c.sortable.map { it.first },
            "searchable" to c.searchable.map { it.name },
            "filterParams" to c.filterFields.map {
                val s = Samples.of(it.type)
                mapOf("name" to it.name, "type" to (s?.openApiType ?: "string"), "format" to s?.openApiFormat)
            },
            "idOpenApiType" to id.openApiType,
            "idOpenApiFormat" to id.openApiFormat,
            "itemSchema" to if (wrap) "ApiResult${c.name}Response" else "${c.name}Response",
            "listRef" to listRef,
            "requiredProps" to c.requestFields.filter { it.required }.map { it.name },
            "requestProps" to props(c.requestFields),
            "responseProps" to props(c.responseFields)
        )
        return PlannedFile(
            "src/main/resources/openapi/${c.name.lowercase()}.yaml", templates.render("OpenApi.yaml.ft", model),
            FileKind.RESOURCE, "OpenAPI 3 specification", c.name
        )
    }

    private fun openApiConfig(c: EntityContext, o: GenerationOptions): PlannedFile {
        val pkg = c.pkg(o.layout.config)
        val model = mapOf("classPackageName" to pkg, "title" to apiTitle(c), "version" to "1.0.0")
        return source(pkg, "OpenApiConfig", templates.render("OpenApiConfig.java.ft", model), "OpenAPI info bean")
    }

    // ------------------------------------------------------------------------------------------ exceptions / uploads

    private fun exceptionHandler(c: EntityContext, o: GenerationOptions): PlannedFile {
        val pkg = c.pkg(o.layout.exception)
        return if (o.standards.standardResponse) {
            source(pkg, "GlobalExceptionHandler", templates.render("GlobalExceptionHandler.java.ft", baseModel(c) + mapOf("classPackageName" to pkg)), "Global exception handler (ApiError)")
        } else {
            source(pkg, "GlobalExceptionHandler", templates.render("SimpleExceptionHandler.java.ft", mapOf("classPackageName" to pkg)), "Global exception handler")
        }
    }

    private fun uploads(c: EntityContext, o: GenerationOptions): List<PlannedFile> {
        val servlet = "import ${if (c.jakarta) "jakarta" else "javax"}.servlet.http.HttpServletResponse;"
        val dtoPkg = c.dtoRootPkg
        val svcPkg = c.pkg(o.layout.service)
        val ctlPkg = c.pkg(o.layout.controller)
        val out = mutableListOf(
            source(dtoPkg, "FileDto", UploadSources.fileDto(dtoPkg), "File metadata DTO"),
            source(svcPkg, "FileService", UploadSources.fileService(svcPkg, dtoPkg, servlet), "File storage service")
        )
        if (o.imageUpload) {
            out += source(ctlPkg, "ImageUploadController", UploadSources.imageController(ctlPkg, svcPkg, servlet), "Image upload endpoint")
            out += source(svcPkg, "ImageService", UploadSources.imageService(svcPkg, servlet), "Image upload service")
        }
        if (o.pdfUpload) {
            out += source(ctlPkg, "PdfUploadController", UploadSources.pdfController(ctlPkg, dtoPkg, svcPkg, servlet), "PDF upload endpoint")
            out += source(svcPkg, "FilePdfService", UploadSources.pdfService(svcPkg, dtoPkg, servlet), "PDF upload service")
        }
        return out
    }

    // ------------------------------------------------------------------------------------------ liquibase

    private fun liquibase(c: EntityContext, o: GenerationOptions, index: Int, total: Int): PlannedFile {
        val lb = o.liquibase!!
        val base = if (total == 1 && lb.fileName.isNotBlank()) lb.fileName else {
            val ts = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(clock())
            "${ts}_${c.name.lowercase().replace("entity", "").trim()}"
        }
        val dir = "src/main/resources/" + lb.packagePath.trim('/').let { if (it.isEmpty()) "" else "$it/" }
        var version = 1
        var path: String
        do {
            path = "$dir$base-${version.toString().padStart(2, '0')}.${lb.format.extension}"
            version++
        } while (exists(path))
        return PlannedFile(path, LiquibaseWriter.write(c, lb.format), FileKind.RESOURCE, "Liquibase changelog for ${c.name}", c.name)
    }

    companion object {
        const val CUSTOM_REGION =
            "    // region custom code (kept when you regenerate)\n    // endregion"
        private val REQUIRED = listOf("NotNull", "NotBlank", "NotEmpty")
    }
}
