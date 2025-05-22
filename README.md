# CraftEngine Host Extension

### Disclaimer
> [!CAUTION]
> Please do not abuse the example hosting methods of services. Strictly comply with the terms of service of Gitee and GitHub. If you need to distribute large quantities of resource packs, please use [S3 Hosting](https://mo-mi.gitbook.io/xiaomomi-plugins/craftengine/plugin-wiki/craftengine/resource-pack/host#s3). The user shall bear full responsibility for any issues arising during use, including those caused by abuse. The developers of this project hereby expressly declare that they shall not be held liable for any consequences resulting from misuse, improper configuration, or violation of platform terms. Users are solely responsible for ensuring their usage complies with all applicable laws, regulations, and third-party service agreements.
> 
> 请勿滥用示例的托管方式服务，严格遵守Gitee和GitHub的服务条款。如需大量分发资源包，请使用[S3托管](https://mo-mi.gitbook.io/xiaomomi-plugins/craftengine/plugin-wiki/craftengine/resource-pack/host#s3)。使用过程中出现的任何问题（包括因滥用导致的问题）均由使用者自行承担全部责任。本项目开发者特此声明：对于因滥用、不当配置或违反平台条款所引发的任何后果概不负责。使用者应确保其使用行为符合所有适用法律法规及第三方服务协议的要求。

### Added gitee hosting and github hosting

```yml
# gitee Example
use-environment-variables: false
type: "gtemc:gitee"
owner: "your_owner_name"
repo: "your_repo_name" # https://gitee.com/projects/new
token: "your_token" # https://gitee.com/profile/personal_access_tokens/new
path: "resourcepacks/resource_pack.zip"
```
> [!TIP]
> Available environment variables: CE_GITEE_TOKEN

```yml
# github Example
use-environment-variables: false
type: "gtemc:gitee"
owner: "your_owner_name"
repo: "your_repo_name" # https://github.com/new
token: "your_token" # https://github.com/settings/tokens/new
branch: "main"
path: "resourcepacks/resource_pack.zip"
```
> [!TIP]
> Available environment variables: CE_GITHUB_TOKEN
