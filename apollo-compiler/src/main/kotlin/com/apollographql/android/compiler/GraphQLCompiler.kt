package com.apollographql.android.compiler

import com.apollographql.android.compiler.ir.CodeGenerationContext
import com.apollographql.android.compiler.ir.CodeGenerationIR
import com.apollographql.android.compiler.ir.TypeDeclaration
import com.squareup.javapoet.JavaFile
import com.squareup.moshi.Moshi
import java.io.File
import java.util.ArrayList

class GraphQLCompiler {
  private val moshi = Moshi.Builder().build()
  private val irAdapter = moshi.adapter(CodeGenerationIR::class.java)

  fun write(args: Arguments): List<File> {
    val codeGenContext = args.codeGenerationContext()
    val javaFiles = codeGenContext.ir.toJavaFiles(codeGenContext)
    javaFiles.write(args.outputDir)
    return javaFiles.outputFiles(args.outputDir)
  }

  fun List<JavaFile>.outputFiles(outputDir: File): List<File> {
    val outputFiles: MutableList<File> = ArrayList()
    forEach {
      var outputDirectory = outputDir
      it.packageName.split("\\.").forEach {
        outputDirectory = outputDirectory.resolve(it)
      }
      outputFiles.add(File(outputDirectory, "${it.typeSpec.name}.java"))
    }
    return outputFiles
  }

  private fun Arguments.codeGenerationContext(): CodeGenerationContext {
    val ir = irAdapter.fromJson(irFile.readText())
    val irPackageName = irFile.absolutePath.formatPackageName()
    val fragmentsPackage = if (irPackageName.isNotEmpty()) "$irPackageName.fragment" else "fragment"
    val typesPackage = if (irPackageName.isNotEmpty()) "$irPackageName.type" else "type"
    val supportedScalarTypeMapping = customTypeMap.supportedScalarTypeMapping(ir.typesUsed)

    return CodeGenerationContext(
        reservedTypeNames = emptyList(),
        typeDeclarations = ir.typesUsed,
        fragmentsPackage = fragmentsPackage,
        typesPackage = typesPackage,
        customTypeMap = supportedScalarTypeMapping,
        nullableValueType = nullableValueType,
        generateAccessors = generateAccessors,
        ir = ir)
  }

  private fun CodeGenerationIR.toJavaFiles(context: CodeGenerationContext): List<JavaFile> {
    val javaFiles: MutableList<JavaFile> = ArrayList()
    fragments.forEach {
      val typeSpec = it.toTypeSpec(context.copy())
      javaFiles.add(JavaFile.builder(context.fragmentsPackage, typeSpec).build())
    }

    typesUsed.supportedTypeDeclarations().forEach {
      val typeSpec = it.toTypeSpec(context.copy())
      javaFiles.add(JavaFile.builder(context.typesPackage, typeSpec).build())
    }
    if (context.customTypeMap.isNotEmpty()) {
      val typeSpec = CustomEnumTypeSpecBuilder(context.copy()).build()
      javaFiles.add(JavaFile.builder(context.typesPackage, typeSpec).build())
    }

    operations.map { OperationTypeSpecBuilder(it, fragments) }
        .forEach {
          val packageName = it.operation.filePath.formatPackageName()
          val typeSpec = it.toTypeSpec(context.copy())
          javaFiles.add(JavaFile.builder(packageName, typeSpec).build())
        }
    return javaFiles
  }

  private fun List<JavaFile>.write(outputDir: File) = forEach { it.writeTo(outputDir) }

  private fun List<TypeDeclaration>.supportedTypeDeclarations() =
      filter { it.kind == TypeDeclaration.KIND_ENUM || it.kind == TypeDeclaration.KIND_INPUT_OBJECT_TYPE }

  private fun Map<String, String>.supportedScalarTypeMapping(typeDeclarations: List<TypeDeclaration>) =
      typeDeclarations.filter { it.kind == TypeDeclaration.KIND_SCALAR_TYPE }
          .associate { it.name to (this[it.name] ?: "Object") }

  companion object {
    const val FILE_EXTENSION = "graphql"
    val OUTPUT_DIRECTORY = listOf("generated", "source", "apollo")
    const val APOLLOCODEGEN_VERSION = "0.10.13"
  }

  data class Arguments(
      val irFile: File,
      val outputDir: File,
      val customTypeMap: Map<String, String>,
      val nullableValueType: NullableValueType,
      val generateAccessors: Boolean)
}
