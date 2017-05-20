package com.apollographql.android.gradle.unit

import com.apollographql.android.gradle.*
import com.moowork.gradle.node.NodePlugin
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

class ApolloAndroidPluginSpec extends Specification {
  def "creates expected tasks under the apollo group and extensions for a default project"() {
    setup:
    def project = ProjectBuilder.builder().build()
    ApolloPluginTestHelper.setupDefaultAndroidProject(project)

    when:
    ApolloPluginTestHelper.applyApolloPlugin(project)
    project.evaluate()

    def debugTask = project.tasks.getByName(String.format(ApolloIRGenTask.NAME, "Debug"))
    def releaseTask = project.tasks.getByName(String.format(ApolloIRGenTask.NAME, "Release"))

    def generateApolloIR = project.tasks.getByName(String.format(ApolloIRGenTask.NAME, ""))

    then:
    debugTask.group.equals(ApolloPlugin.TASK_GROUP)
    debugTask.description.equals("Generate an IR file using apollo-codegen for Debug GraphQL queries")

    releaseTask.group.equals(ApolloPlugin.TASK_GROUP)
    releaseTask.description.equals("Generate an IR file using apollo-codegen for Release GraphQL queries")

    debugTask.dependsOn.contains(ApolloCodeGenInstallTask.NAME)
    releaseTask.dependsOn.contains(ApolloCodeGenInstallTask.NAME)

    generateApolloIR.dependsOn.contains(project.tasks.getByName(String.format(ApolloIRGenTask.NAME, "Debug")))
    generateApolloIR.dependsOn.contains(project.tasks.getByName(String.format(ApolloIRGenTask.NAME, "Release")))

    project.android.sourceSets.all { sourceSet ->
      assert (sourceSet.extensions.findByName("graphql")) != null
      assert (sourceSet.extensions.findByType(GraphQLSourceDirectorySet.class)) != null
    }

    assert (project.extensions.findByName("apollo")) != null
    assert (project.extensions.findByType(ApolloExtension.class)) != null
  }

  def "creates expected tasks under the apollo group and extensions for a product-flavoured project"() {
    setup:
    def project = ProjectBuilder.builder().build()
    def flavors = ["Demo", "Full"]
    ApolloPluginTestHelper.setupAndroidProjectWithProductFlavours(project)

    when:
    ApolloPluginTestHelper.applyApolloPlugin(project)
    project.evaluate()

    then:
    flavors.each { flavor ->
      def debugTask = project.tasks.getByName(String.format(ApolloIRGenTask.NAME, "${flavor}Debug"))
      def releaseTask = project.tasks.getByName(String.format(ApolloIRGenTask.NAME, "${flavor}Release"))

      assert (debugTask.group) == ApolloPlugin.TASK_GROUP
      assert (debugTask.description) == "Generate an IR file using apollo-codegen for " + flavor + "Debug GraphQL queries"

      assert (releaseTask.group) == ApolloPlugin.TASK_GROUP
      assert (releaseTask.description) == "Generate an IR file using apollo-codegen for " + flavor + "Release GraphQL queries"

      project.android.sourceSets.all { sourceSet ->
        assert (sourceSet.extensions.findByName("graphql")) != null
        assert (sourceSet.extensions.findByType(GraphQLSourceDirectorySet.class)) != null
      }

      assert (project.extensions.findByName("apollo")) != null
      assert (project.extensions.findByType(ApolloExtension.class)) != null
    }
  }


  def "adds apollo-runtime dependency if not skipped and not found in compile dep list"() {
    given:
    def project = ProjectBuilder.builder().build()
    ApolloPluginTestHelper.setupDefaultAndroidProject(project)

    when:
    ApolloPluginTestHelper.applyApolloPlugin(project)
    project.evaluate()

    then:
    def apolloRuntime = project.configurations.getByName("compile").dependencies.find {
      it.group == "com.apollographql.apollo" && it.name == "apollo-runtime"
    }
    assert apolloRuntime != null
  }

  def "doesn't add apollo-runtime dependency if skip property is set" () {
    given:
    def project = ProjectBuilder.builder().build()
    ApolloPluginTestHelper.setupDefaultAndroidProject(project)

    project.beforeEvaluate {
      System.setProperty("apollographql.skipRuntimeDep", "true")
    }

    when:
    ApolloPluginTestHelper.applyApolloPlugin(project)
    project.evaluate()

    then:
    def compileDepSet = project.configurations.getByName("compile").dependencies
    assert compileDepSet.isEmpty()
  }
}
