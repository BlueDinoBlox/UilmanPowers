# UilmanPowers — GitHub build

1. Create a new GitHub repository (choose Public or Private).
2. Unzip this archive locally and upload **the contents** of this folder (including `.github`) to the root of the repository. Do not upload the ZIP alone.
3. Commit files to the `main` branch.
4. Open **Actions** > **Build UilmanPowers JAR** > **Run workflow** (or select the automatically triggered build).
5. After the green checkmark, open the run and download the **UilmanPowers-plugin-JAR** artifact at the bottom of the run page. Unzip that artifact to get the `.jar`.
6. Place the `.jar` in your Paper server's `plugins` folder and restart.

Console-only commands: `uilman give <player>`, `uilman remove <player>`, `uilman list`.

**Important:** Paper API `26.3.build.+` has not been verified to exist. If GitHub Actions says it cannot resolve that dependency, the build will fail until `build.gradle` is updated to a real published version. GitHub Actions does not automatically fix source errors. Java 25 and Gradle 9.2.1 are configured in the workflow.
