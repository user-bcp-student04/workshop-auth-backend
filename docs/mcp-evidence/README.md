# MCP laboratory evidence

Date: 2026-10-21

Repository: `user-bcp-student04/workshop-auth-backend`

## Issue evidence

- Created issue [#9](https://github.com/user-bcp-student04/workshop-auth-backend/issues/9), titled `MCP local - write permission test`.
- Listed open issues with the GitHub Official MCP tool. The result included #10 `Test Issue` and #9 `MCP local - write permission test`, both open.

## Project evidence

- Created the GitHub project `ReqFlow` for `user-bcp-student04`; project number: `2`.
- Created the text field `Hallazgo origen`.
- Added issue #9 to project #2.

## Commands

```powershell
gh project create --owner user-bcp-student04 --title ReqFlow --format json --jq .number
gh project field-create 2 --owner user-bcp-student04 --name "Hallazgo origen" --data-type TEXT
gh project item-add 2 --owner user-bcp-student04 --url https://github.com/user-bcp-student04/workshop-auth-backend/issues/9
```

## MCP configuration snapshot

`mcp.json` is a copy of the workspace `.vscode/mcp.json`. It was checked for tokens; no token values were present in the configuration.
