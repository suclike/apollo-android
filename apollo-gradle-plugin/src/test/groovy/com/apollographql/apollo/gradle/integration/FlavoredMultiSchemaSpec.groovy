package com.apollographql.apollo.gradle.integration

import org.apache.commons.io.FileUtils
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Shared
import spock.lang.Specification

import static com.apollographql.apollo.gradle.ApolloPluginTestHelper.*
import static org.apache.commons.io.FileUtils.copyFile


class FlavoredMultiSchemaSpec extends Specification {
  @Shared File testProjectDir

  def setupSpec() {
    testProjectDir = setupFlavoredAndroidProject()
  }

  def "generates expected outputs for the demo debug variant"() {
    setup:
    "GitHunt and Star Wars schema under src/demoDebug and an invalid GitHunt schema file and Front Page schema under src/main." +
        " Two query files for GitHunt and one for Star wars under src/demoDebug. One for Front Page and two for Star Wars under src/main"
    when:
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir)
        .withPluginClasspath()
        .withArguments("generateDemoDebugApolloClasses")
        .forwardStdError(new OutputStreamWriter(System.err))
        .build()

    then:
    result.task(":generateDemoDebugApolloClasses").outcome == TaskOutcome.SUCCESS

    assertDemoDebugGenerationSucces()
  }

  def "generateDemoDebugApolloClasses is up-to-date after running once"() {
    setup: "a project with a previous run of generateDemoDebugApolloClasses"
    when:
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir)
        .withPluginClasspath()
        .withArguments("generateDemoDebugApolloClasses")
        .forwardStdError(new OutputStreamWriter(System.err))
        .build()

    then:
    result.task(":generateDemoDebugApolloClasses").outcome == TaskOutcome.UP_TO_DATE

  }

  def "exception thrown for the generateDemoReleaseApolloClasses task due to the invalid GitHunt schema file"() {
    setup:
    "a project with no schema files under the release source set, a valid schema and query files under src/main and" +
        "an invalid schema files under src/main"
    when:
    GradleRunner.create()
        .withProjectDir(testProjectDir)
        .withPluginClasspath()
        .withArguments("generateDemoReleaseApolloClasses")
        .build()

    then:
    thrown Exception
  }

  def "build task runs succesfully and generates the expected outputs"() {
    setup: "a project with no invalid schema files and one query file under a release source set"
    FileUtils.forceDelete(new File(testProjectDir.getAbsolutePath() + "/src/main/graphql/com/githunt"))

    when:
    def result = GradleRunner.create()
        .withProjectDir(testProjectDir)
        .withPluginClasspath()
        .withArguments("build", "-Dapollographql.skipRuntimeDep=true")
        .forwardStdError(new OutputStreamWriter(System.err))
        .build()

    then:
    result.task(":generateDemoDebugApolloClasses").outcome == TaskOutcome.UP_TO_DATE
    result.task(":build").outcome == TaskOutcome.SUCCESS

    // IR Files generated successfully
    assert new File(testProjectDir, "build/generated/source/apollo/generatedIR/demoRelease/src/main/graphql/com/frontpage/api/DemoReleaseAPI.json").isFile()
    assert new File(testProjectDir, "build/generated/source/apollo/generatedIR/demoRelease/src/release/graphql/com/starwars/api/DemoReleaseAPI.json").isFile()

    assert new File(testProjectDir, "build/generated/source/apollo/generatedIR/fullRelease/src/main/graphql/com/frontpage/api/FullReleaseAPI.json").isFile()
    assert new File(testProjectDir, "build/generated/source/apollo/generatedIR/fullRelease/src/release/graphql/com/starwars/api/FullReleaseAPI.json").isFile()

    // OperationIdMap.json generated successfully
    assert new File(testProjectDir, "build/generated/source/apollo/generatedIR/demoRelease/src/main/graphql/com/frontpage/api/DemoReleaseOperationIdMap.json").isFile()
    assert new File(testProjectDir, "build/generated/source/apollo/generatedIR/demoRelease/src/release/graphql/com/starwars/api/DemoReleaseOperationIdMap.json").isFile()

    assert new File(testProjectDir, "build/generated/source/apollo/generatedIR/fullRelease/src/main/graphql/com/frontpage/api/FullReleaseOperationIdMap.json").isFile()
    assert new File(testProjectDir, "build/generated/source/apollo/generatedIR/fullRelease/src/release/graphql/com/starwars/api/FullReleaseOperationIdMap.json").isFile()

    // generates java classes for queries under the release source set
    assert new File(testProjectDir, "build/generated/source/apollo/com/starwars/api/starship/StarshipQuery.java").isFile()
    assertDemoDebugGenerationSucces()
  }


  def cleanupSpec() {
    FileUtils.deleteDirectory(testProjectDir)
  }

  private void assertDemoDebugGenerationSucces() {
    // IR Files generated successfully
    assert new File(testProjectDir, "build/generated/source/apollo/generatedIR/demoDebug/src/demoDebug/graphql/com/githunt/api/DemoDebugAPI.json").isFile()
    assert new File(testProjectDir, "build/generated/source/apollo/generatedIR/demoDebug/src/demoDebug/graphql/com/starwars/api/DemoDebugAPI.json").isFile()
    assert new File(testProjectDir, "build/generated/source/apollo/generatedIR/demoDebug/src/main/graphql/com/frontpage/api/DemoDebugAPI.json").isFile()

    // OperationIdMap.json file created
    assert new File(testProjectDir, "build/generated/source/apollo/generatedIR/demoDebug/src/demoDebug/graphql/com/githunt/api/DemoDebugOperationIdMap.json").isFile()
    assert new File(testProjectDir, "build/generated/source/apollo/generatedIR/demoDebug/src/demoDebug/graphql/com/starwars/api/DemoDebugOperationIdMap.json").isFile()
    assert new File(testProjectDir, "build/generated/source/apollo/generatedIR/demoDebug/src/main/graphql/com/frontpage/api/DemoDebugOperationIdMap.json").isFile()

    // Java classes generated successfully
    // For Front Page
    assert new File(testProjectDir, "build/generated/source/apollo/com/frontpage/api/fragment/PostDetails.java").isFile()

    assert new File(testProjectDir, "build/generated/source/apollo/com/frontpage/api/posts/UpvoteMutation.java").isFile()

    // For Star Wars
    assert new File(testProjectDir, "build/generated/source/apollo/com/starwars/api/fragment/HeroDetails.java").isFile()
    assert new File(testProjectDir, "build/generated/source/apollo/com/starwars/api/type/ReviewInput.java").isFile()
    assert new File(testProjectDir, "build/generated/source/apollo/com/starwars/api/type/Episode.java").isFile()

    assert new File(testProjectDir, "build/generated/source/apollo/com/starwars/api/hero/HeroNameQuery.java").isFile()
    assert new File(testProjectDir, "build/generated/source/apollo/com/starwars/api/hero/HeroAndFriendsQuery.java").isFile()
    assert new File(testProjectDir, "build/generated/source/apollo/com/starwars/api/review/CreateAwesomeReviewMutation.java").isFile()
    assert new File(testProjectDir, "build/generated/source/apollo/com/starwars/api/review/CreateReviewForEpisodeMutation.java").isFile()

    // For GitHunt
    assert new File(testProjectDir, "build/generated/source/apollo/com/githunt/api/fragment/FeedCommentFragment.java").isFile()
    assert new File(testProjectDir, "build/generated/source/apollo/com/githunt/api/fragment/RepositoryFragment.java").isFile()
    assert new File(testProjectDir, "build/generated/source/apollo/com/githunt/api/type/FeedType.java").isFile()

    assert new File(testProjectDir, "build/generated/source/apollo/com/githunt/api/feed/FeedQuery.java").isFile()
    assert new File(testProjectDir, "build/generated/source/apollo/com/githunt/api/profile/CurrentUserForLayoutQuery.java").isFile()
  }

  private static File setupFlavoredAndroidProject() {
    def destDir = createTempTestDirectory("flavoredWithMultipleSchemas")
    prepareProjectTestDir(destDir, ProjectType.Android, "flavoredWithMultipleSchemas", "flavored")
    String schemaFilesFixtures = "src/test/testProject/android/schemaFilesFixtures"
    copyFile(new File(schemaFilesFixtures + "/invalidSchema.json"), new File("$destDir/src/main/graphql/com/githunt/api/schema.json"))
    copyFile(new File(schemaFilesFixtures + "/frontpage.json"), new File("$destDir/src/main/graphql/com/frontpage/api/schema.json"))
    copyFile(new File(schemaFilesFixtures + "/githunt.json"), new File("$destDir/src/demoDebug/graphql/com/githunt/api/schema.json"))
    copyFile(new File(schemaFilesFixtures + "/starwars.json"), new File("$destDir/src/demoDebug/graphql/com/starwars/api/schema.json"))
    copyFile(new File(schemaFilesFixtures + "/starwars.json"), new File("$destDir/src/release/graphql/com/starwars/api/schema.json"))
    return destDir
  }
}
