#!/usr/bin/env kotlin

@file:DependsOn("it.krzeminski:github-actions-kotlin-dsl:0.10.0")

import it.krzeminski.githubactions.actions.actions.CacheV2
import it.krzeminski.githubactions.actions.actions.CheckoutV2
import it.krzeminski.githubactions.actions.docker.BuildPushActionV2
import it.krzeminski.githubactions.actions.docker.LoginActionV1
import it.krzeminski.githubactions.actions.docker.SetupBuildxActionV1
import it.krzeminski.githubactions.actions.nobrayner.DiscordWebhookV1
import it.krzeminski.githubactions.domain.RunnerType.UbuntuLatest
import it.krzeminski.githubactions.domain.triggers.PullRequest
import it.krzeminski.githubactions.domain.triggers.Push
import it.krzeminski.githubactions.dsl.workflow
import it.krzeminski.githubactions.dsl.expr
import it.krzeminski.githubactions.yaml.writeToFile
import java.nio.file.Paths

val workflow = workflow(
    name = "Docker build & push",
    on = listOf(
        Push(
            branches = listOf("main"),
        ),
        PullRequest(
            branches = listOf("main"),
        )
    ),
    sourceFile = Paths.get(".github/workflows/docker_workflow.main.kts"),
    targetFile = Paths.get(".github/workflows/docker_workflow.yml"),
) {
    val buildJob = job(name = "build_job", runsOn = UbuntuLatest) {
        uses(
            name = "Check out",
            action = CheckoutV2(
                fetchDepth = CheckoutV2.FetchDepth.Value(0)
            )
        )
        uses(
            name = "Cache",
            action = CacheV2(
                path = listOf(
                    "~/.gradle",
                    ".gradle",
                    "~/.docker",
                ),
                key = "${expr("runner.os")}-gradle-cache-${expr("github.ref")}-${expr("hashFiles('**/build.gradle.kts', 'version.properties')")}"
            )
        )
        uses(
            name = "Docker Login",
            action = LoginActionV1(
                username = expr("secrets.DOCKER_HUB_USERNAME"),
                password = expr("secrets.DOCKER_HUB_ACCESS_TOKEN"),
            )
        )
        uses(
            name = "Docker Setup buildx",
            action = SetupBuildxActionV1()
        )
        val dockerBuildPush = uses(
            name = "Build and push",
            action = BuildPushActionV2(
                context = ".",
                file = "./Dockerfile",
                push = true,
                tags = listOf("${expr("secrets.DOCKER_HUB_USERNAME")}/${expr("secrets.DOCKER_HUB_REPOSITORY")}:latest"),
            )
        )
        run(
            name = "image digest",
            command = "echo ${expr(dockerBuildPush.outputs.digest)}",
        )
    }
    job(
        name = "discord-notification",
        runsOn = UbuntuLatest,
        needs = listOf(
            buildJob
        ),
        condition = expr("always()"),
    ) {
        uses(
            name = "Discord Workflow Status Notifier",
            action = DiscordWebhookV1(
                githubToken = expr("github.token"),
                discordWebhook = expr("secrets.WEBHOOK_URL"),
                includeDetails = true,
            ),
            condition = "always()"
        )
    }
}

workflow.writeToFile(addConsistencyCheck = true)
