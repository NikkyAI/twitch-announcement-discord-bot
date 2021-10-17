import ch.qos.logback.core.joran.spi.ConsoleTarget

//def environment = System.getenv().getOrDefault("ENVIRONMENT", "production")
def defaultLevel = DEBUG

//if (environment == "debug") {
//    defaultLevel = DEBUG
//
//    // Silence warning about missing native PRNG on Windows
//    logger("io.ktor.util.random", ERROR)
//}

static String w(String p, String c) {
    return "$p($c)"
}

String fileRef = '%-20(.\\(%F:%L\\))'

String fullPattern = w("%-55",
        '%d{dd-MM-yyyy\'T\'HH:mm:ssZ} [%thread] ' + fileRef as String
)+ ' %-5level - %msg%n'

String consolePattern = ""
if (System.getenv("DOCKER_LOGGING") == "true") {
    consolePattern = fileRef + ' ' +  w('%boldWhite', '%-5level') +' - %msg%n'
} else {
//    consolePattern = "%-55(%boldBlue(%d{dd-MM-yyyy'T'HH:mm:ssZ}) %highlight([%thread] %-20(.\\(%F:%L\\)))) %-5level - %msg%n"
    consolePattern = w('%cyan', '%d{dd-MM-yyyy\'T\'HH:mm:ssZ}') + ' ' + w("%-60", " [%thread] $fileRef" as String)+ ' ' + w('%highlight', '%-5level') + ' - %msg%n'
}

appender("CONSOLE", ConsoleAppender) {
    encoder(PatternLayoutEncoder) {
        pattern = consolePattern
    }

    target = ConsoleTarget.SystemOut
}


appender("FILE", FileAppender) {
    file = "logs/latest.log"
    encoder(PatternLayoutEncoder) {
        pattern = fullPattern
    }
}

def bySecond = timestamp("yyyyMMdd'T'HHmmss")
appender("FILE_LATEST", FileAppender) {
    file = "logs/log-${bySecond}.log"
    encoder(PatternLayoutEncoder) {
        pattern = fullPattern
    }
}


root(defaultLevel, ["CONSOLE", "FILE", "FILE_LATEST"])