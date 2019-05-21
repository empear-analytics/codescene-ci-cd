# codescene-ci-cd


[![Build Status](https://travis-ci.org/empear-analytics/codescene-ci-cd.svg)](https://travis-ci.org/empear-analytics/codescene-ci-cd)
[![Latest release](https://img.shields.io/github/release/empear-analytics/codescene-ci-cd.svg)](https://github.com/empear-analytics/codescene-ci-cd/releases/latest)

A bridge application for integrating
[CodeScene](https://empear.com/how-it-works/) by Empear into CI/CD build pipelines


CodeScene identifies and prioritizes technical debt, while at the same time uncovering and measuring social factors of the organization behind
the system. The earlier you can react to any potential finding, the better. That’s why
CodeScene offers integration points that let you incorporate the analysis
results into your build pipeline.


![Component Diagram](component-diagram.png)


The codescene-ci-cd application is runnable from build scripts in a CI-CD system, and uses the CodeScene Delta analysis API to perform a delta analysis and display the results in the build output. In addition to this, the results can be attached as review comments on Merge/Pell Requests:

![Component Diagram](analysis-results.png)

## Capabilities, Use Cases, and Examples

This application lets you use CodeScene’s Delta Analysis to:
* Prioritize code reviews based on the risk of the commits.
* Specify quality gates for the goals specified on identified hotspots (see [Managing Technical Debt](https://empear.com/blog/manage-technical-debt-with-augmented-code-analysis/)).
* Specify quality gates that trigger in case the Code Health of a hotspot declines.

## Usage

The application can be run on its own through:

    $ java -jar codescene-ci-cd-1.0.0-standalone.jar [options]

With that said, the recommended usage in a build environment is to use the docker image available at [Docker Hub](https://hub.docker.com/r/empear/codescene-ci-cd) by configuring a build job using the image in the build file for a project.

Being a command line utility, _codescene-cd-cd_ is configured through numerous command line options:

```
Usage: codescene-ci-cd [options]
Options:
    -h, --help
        --codescene-delta-analysis-url URL          CodeScene Delta Analysis URL
    -u, --codescene-user USER                       CodeScene User
    -p, --codescene-password PWD                    CodeScene Password
    -r, --codescene-repository REPO                 CodeScene Repository
        --analyze-individual-commits                Individual Commits
        --analyze-branch-diff                       By Branch
        --pass-on-failed-analysis                   Pass Build on Failed Analysis
        --fail-on-high-risk                         Fail Build on High Risk
        --fail-on-failed-goal                       Fail Build on Failed Goals
        --fail-on-declining-code-health             Fail Build on Code Health Decline
        --create-gitlab-note                        Create Note For Gitlab Merge Request
        --create-github-comment                     Create Comment For GitHub Pull Request
        --create-bitbucket-comment                  Create Comment For Bitbucket Pull Request
        --[no-]log-result                           Log the result (by printing)
        --coupling-threshold-percent THRESHOLD  75  Temporal Coupling Threshold (in percent)
        --risk-threshold THRESHOLD              9   Risk Threshold
        --previous-commit SHA                       Previous Commit Id
        --current-commit SHA                        Current Commit Id
        --base-revision SHA                         Base Revision Id
        --gitlab-api-url URL                        GitLab API URL
        --gitlab-api-token TOKEN                    GitLab API Token
        --gitlab-project-id ID                      GitLab Project ID
        --gitlab-merge-request-iid IID              GitLab Merge Request IID
        --github-api-url URL                        GitHub API URL
        --github-api-token TOKEN                    GitHub API Token
        --github-owner OWNER                        GitHub Repository Owner
        --github-repo REPO                          GitHub Repository Name
        --github-pull-request-id ID                 GitHub Pull Request ID
        --bitbucket-api-url URL                     BitBucket API URL
        --bitbucket-user USER                       BitBucket User
        --bitbucket-password PASSWORD               BitBucket Password
        --bitbucket-repo REPO                       BitBucket Repository Name
        --bitbucket-pull-request-id ID              BitBucket Pull Request ID
        --result-path FILENAME                      Path where JSON output is generated
        --http-timeout TIMEOUT-MS                   Timeout for http API calls
```
In a build pipeline, many of these options are common to all projects, and are thus easiest defined as variables set in the CI/CD-system, see examples below.

The `codescene-*` options specify how to connect to CodeScene and must match the settings in codescene itself. The `bitbucket-*`, `gitlab-*` and `github-*` options specify how to connect to the repo provider for creating comments on Merge/Pull Requests. See specific examples below for details. 

The `result-path` saves the analysis results as a json file.

The `http-timeout` options specifies the timeout in milliseconds for all http requests. In some situations it may benecessary to specify a value greater than the default 10,000 ms.

### Configure GitLab for codescene-ci-cd

Enable the CodeScene integration by adding new jobs in the projects _.gitlab-cy.yml_ file. This is an example of two jobs, one for merge requests, and one for pushed commits:

```
include: /templates/codescene-ci-cd.gitlab.yml

stages:
  - codescene-ci-cd

run-codescene-ci-cd:
  stage: codescene-ci-cd
  extends: .template-codescene-ci-cd-on-push

run-codescene-ci-cd-on-merge-request:
  stage: codescene-ci-cd
  extends: .template-codescene-ci-cd-on-merge-request
```

In this example, `CODESCENE-*` variables are set in the gitlab GUI.
The `CI_*` variables are all built-in variables in GitLab. The `run-codescene-ci-cd` job runs analysis on individual commits. The `run-codescene-ci-cd-on-merge-request` runs analysis on an entire branch, and submits the results as a merge request comment.

####  Delta Analysis Settings


By checking the _**Use Biomarkers**_ option, CodeScene warns about files that seem to degrade in quality through issues introduced in the current changeset.

Finally, you enable the Quality Gates in the configuration too.

#### CodeScene API Configuration

The CodeScene API configuration section has to match the information specified inside CodeScene itself and retrievable from the analysis configuration (Project configuration -> Delta Analysis):

![Project Configuration - Delta Analysis](project-config-delta-analysis.png)

API Credentials should be added via [jenkins credentials plugin](https://wiki.jenkins-ci.org/display/JENKINS/Credentials+Plugin).
Check [Injecting Secrets into Jenkins Build Jobs](https://support.cloudbees.com/hc/en-us/articles/203802500-Injecting-Secrets-into-Jenkins-Build-Jobs) for more details.



## Manual build

You can build the latest version of _codescene-ci-cd_ by running `lein uberjar`. The corresponding docker image is then built using `docker build -t empear/codescene-ci-cd`.
Run _codescene-ci-cd_ inside the docker image using `docker run -it empear/codescene-ci-cd [options]`

## Contributing

You're encouraged to submit [pull
requests](https://github.com/empear-analytics/codescene-ci-cd/pulls),
and to [propose features and discuss
issues](https://github.com/empear-analytics/codescene-ci-cd/issues).

## License

Licensed under the [MIT License](LICENSE).



## GitLab

## Circle CI
```
version: 2.1

jobs:
  codescene-ci-cd:
    docker:
      - image: empear/codescene-ci-cd
    steps:
      - checkout
      - run: 
          name: CodeScene Delta Analysis
          command:
            if [[ -z $CIRCLE_PULL_REQUEST ]] ; then 
              export PREVIOUS_COMMIT=$(git rev-parse HEAD^);
              java -jar /codescene-ci-cd/codescene-ci-cd-standalone.jar
                --codescene-delta-analysis-url $CODESCENE_URL$CODESCENE_DELTA_ANALYSIS_URL
                --codescene-user $CODESCENE_USER
                --codescene-password $CODESCENE_PASSWORD
                --codescene-repository $CIRCLE_PROJECT_REPONAME
                --pass-on-failed-analysis
                --fail-on-failed-goal
                --fail-on-declining-code-health
                --analyze-individual-commits
                --coupling-threshold-percent $CODESCENE_COUPLING_THRESHOLD_PERCENT
                --risk-threshold $CODESCENE_RISK_THRESHOLD    
                --current-commit $CIRCLE_SHA1
                --previous-commit $PREVIOUS_COMMIT
                --http-timeout 10000
                --log-result;
            else
              export PREVIOUS_COMMIT=$(git rev-parse HEAD^);
              export PULL_REQUEST_ID=${CIRCLE_PULL_REQUEST##*/};
              java -jar /codescene-ci-cd/codescene-ci-cd-standalone.jar
                --codescene-delta-analysis-url $CODESCENE_URL$CODESCENE_DELTA_ANALYSIS_URL
                --codescene-user $CODESCENE_USER
                --codescene-password $CODESCENE_PASSWORD
                --codescene-repository $CIRCLE_PROJECT_REPONAME
                --pass-on-failed-analysis
                --fail-on-failed-goal
                --fail-on-declining-code-health
                --analyze-branch-diff
                --coupling-threshold-percent $CODESCENE_COUPLING_THRESHOLD_PERCENT
                --risk-threshold $CODESCENE_RISK_THRESHOLD
                --current-commit $CIRCLE_SHA1
                --base-revision master
                --create-github-comment
                --github-api-url $GITHUB_API_URL
                --github-api-token $GITHUB_API_TOKEN
                --github-owner $CIRCLE_PROJECT_USERNAME
                --github-repo $CIRCLE_PROJECT_REPONAME
                --github-pull-request-id $PULL_REQUEST_ID
                --http-timeout 10000
                --log-result;
            fi  

workflows:
  version: 2
  my-workflow:
    jobs:
      - codescene-ci-cd:
          context: codescene-ci-cd
```

##  


## Bitbucket

## License
