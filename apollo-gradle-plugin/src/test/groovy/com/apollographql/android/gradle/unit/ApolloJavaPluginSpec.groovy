package com.apollographql.android.gradle.unit

import com.apollographql.android.gradle.*
import com.moowork.gradle.node.NodePlugin
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

class ApolloJavaPluginSpec extends Specification {
  def "creates the expected tasks and extensions under the apollo group"() {
    setup:
    def project = ProjectBuilder.builder().build()
    ApolloPluginTestHelper.setupJavaProject(project)

    when:
    ApolloPluginTestHelper.applyApolloPlugin(project)
    project.evaluate()

    def irGenMainTask = project.tasks.getByName(String.format(ApolloIRGenTask.NAME, "Main"))
    def classGenMainTask = project.tasks.getByName(String.format(ApolloClassGenTask.NAME, "Main"))

    then:
    irGenMainTask.group.equals(ApolloPlugin.TASK_GROUP)
    irGenMainTask.description.equals("Generate an IR file using apollo-codegen for Main GraphQL queries")

    classGenMainTask.group.equals(ApolloPlugin.TASK_GROUP)
    classGenMainTask.description.equals("Generate Android classes for Main GraphQL queries")

    project.sourceSets.all { sourceSet ->
      assert (sourceSet.extensions.findByName("graphql")) != null
      assert (sourceSet.extensions.findByType(GraphQLSourceDirectorySet.class)) != null
    }

    assert (project.extensions.findByName("apollo")) != null
    assert (project.extensions.findByType(ApolloExtension.class)) != null
  }
}
