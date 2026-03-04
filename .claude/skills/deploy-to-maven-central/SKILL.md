---
name: deploy-to-maven-central
description: Deploys the library to OSSRH staging and creates a pre-release, but publishing must be done manually via the Sonatype web UI.
disable-model-invocation: true
allowed-tools: Bash(*)
---

# Deploy to Maven Central (OSSRH Staging)

Deploy both `auth` and `store` flavours of the Android auth library to the OSSRH staging repository, then upload the staged deployment for publishing.

## Steps

### 1. Validate `local.properties`

Check that the file `local.properties` in the project root exists and contains all of the following keys:

- `signing.keyId`
- `signing.password`
- `signing.secretKeyRingFile`
- `ossrhUsername`
- `ossrhPassword`

If the file is missing or any key is absent, stop and tell the user:

> `local.properties` must contain the following properties:
>
> ```
> signing.keyId=<gpg signing key>
> signing.password=<gpg signing password>
> signing.secretKeyRingFile=<gpg keyring>
> ossrhUsername=<ossrhUsername>
> ossrhPassword=<ossrhPassword>
> ```

Extract the values of `ossrhUsername` and `ossrhPassword` from `local.properties` for use in later steps. Store them in shell variables `OSSRH_USER` and `OSSRH_PASS`.

### 2. Publish the `auth` flavour

Run Gradle to assemble and publish the **auth** (regular) flavour:

```bash
./gradlew :auth-lib:assembleRelease :auth-lib:publishAuthReleasePublicationToOssrh-staging-apiRepository
```

Wait for this to succeed before continuing.

### 3. Publish the `store` flavour

Run Gradle to assemble and publish the **store** flavour:

```bash
./gradlew :auth-lib:assembleRelease :auth-lib:publishStoreReleasePublicationToOssrh-staging-apiRepository
```

Wait for this to succeed before continuing.

### 4. Generate a base64 auth token

Compute a base64-encoded token from the credentials for the OSSRH staging API:

```bash
TOKEN=$(printf "$OSSRH_USER:$OSSRH_PASS" | base64)
```

### 5. Fetch the staging repository list

Call the OSSRH staging API to get the list of repositories:

```bash
curl -s --fail \
  -H "Authorization: Bearer $TOKEN" \
  https://ossrh-staging-api.central.sonatype.com/manual/search/repositories
```

### 6. Extract the repository key

Parse the JSON response to extract the repository key. It is the `key` field of the first (or only) entry in the `repositories` array. Save this as `REPO_KEY`.

### 7. Upload the repository

POST to the upload endpoint to promote the staged repository:

```bash
curl -s --fail -X POST \
  -H "Authorization: Bearer $TOKEN" \
  "https://ossrh-staging-api.central.sonatype.com/manual/upload/repository/$REPO_KEY"
```

### 8. Report result

If all steps succeeded, tell the user:

> Deployment uploaded successfully. To complete publishing, log in to the [Sonatype OSSRH portal](https://central.sonatype.com) and manually release the staged repository.

If any step failed, report the error and stop.
