# CraftEngine Host Extension

### Added gitlab hosting and github hosting

```yml
# gitlab Example
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
