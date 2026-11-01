#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
mkdir -p build/receipt-smoke
cat > build/receipt-smoke/ios-test-isolate.gradle <<'GRADLE'
gradle.projectsEvaluated {
    def app = gradle.rootProject.project(':composeApp')
    app.tasks.named('compileTestKotlinIosSimulatorArm64').configure { task ->
        task.setSource(app.fileTree('src/iosTest/kotlin'))
    }
}
GRADLE

./gradlew :composeApp:iosSimulatorArm64Test --no-daemon \
    -I build/receipt-smoke/ios-test-isolate.gradle
