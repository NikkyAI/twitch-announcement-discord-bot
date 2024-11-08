#!/usr/bin/env kotlin

@file:Repository("https://repo.maven.apache.org/maven2/")
@file:DependsOn("io.github.typesafegithub:github-workflows-kt:3.0.1")
@file:Repository("https://bindings.krzeminski.it")
@file:DependsOn("actions:checkout:v4")
@file:DependsOn("docker:login-action:v3")
@file:DependsOn("docker:setup-buildx-action:v3")
@file:DependsOn("docker:build-push-action:v5")
@file:DependsOn("nobrayner:discord-webhook:v1")

import io.github.typesafegithub.workflows.actions.actions.Checkout
import io.github.typesafegithub.workflows.actions.docker.BuildPushAction
import io.github.typesafegithub.workflows.actions.docker.LoginAction
import io.github.typesafegithub.workflows.actions.docker.SetupBuildxAction
import io.github.typesafegithub.workflows.actions.nobrayner.DiscordWebhook
import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.domain.triggers.PullRequest
import io.github.typesafegithub.workflows.domain.triggers.Push
import io.github.typesafegithub.workflows.dsl.expressions.expr
import io.github.typesafegithub.workflows.dsl.workflow

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
    sourceFile = __FILE__,
) {
    val buildJob = job(
        id = "build_job",
        name = "Build Job",
        runsOn = UbuntuLatest
    ) {
        uses(
            name = "Check out",
            action = Checkout(
                fetchDepth = Checkout.FetchDepth.Value(0)
            )
        )
        uses(
            name = "Docker Login",
            action = LoginAction(
                username = expr("secrets.DOCKER_HUB_USERNAME"),
                password = expr("secrets.DOCKER_HUB_ACCESS_TOKEN"),
            )
        )
        uses(
            name = "Docker Setup buildx",
            action = SetupBuildxAction()
        )
        val dockerBuildPush = uses(
            name = "Build and push",
            action = BuildPushAction(
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
        id = "discord_notification",
        name = "Discord Notification",
        runsOn = UbuntuLatest,
        needs = listOf(
            buildJob
        ),
        condition = expr("always()"),
    ) {
        uses(
            name = "Discord Workflow Status Notifier",
            action = DiscordWebhook(
                githubToken = expr("github.token"),
                discordWebhook = expr("secrets.WEBHOOK_URL"),
                includeDetails = true,
            ),
            condition = "always()"
        )
    }
}

//workflow.writeToFile(addConsistencyCheck = true)
