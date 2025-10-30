Here’s an example of an agent.yaml configuration file that you can use to define a Copilot agent (e.g., for GitHub Copilot Workspace or Copilot Agents) that can create JIRA tickets.

It includes the right structure, metadata, and tool definition to connect to your JIRA REST API securely.


---

🧩 Example: agent.yaml

name: jira-ticket-creator
description: >
  An AI-powered assistant that creates and manages JIRA tickets.
  It can create new tickets, update their status, assign users,
  and retrieve ticket details via JIRA REST API.

version: 1.0.0

instructions: |
  You are a helpful assistant specialized in creating and managing JIRA issues.
  When asked to create a JIRA ticket, collect the following information:
    - Project key (e.g., FRMT)
    - Issue type (e.g., Bug, Task, Story)
    - Summary
    - Description
    - Assignee (optional)
    - Priority (optional)
  Then use the JIRA REST API to create the issue.

tools:
  - type: openapi
    name: jira_api
    description: JIRA Cloud REST API for issue management.
    url: https://your-domain.atlassian.net/rest/api/3/swagger.json
    auth:
      type: oauth2
      authorizationUrl: https://auth.atlassian.com/authorize
      tokenUrl: https://auth.atlassian.com/oauth/token
      scopes:
        - read:jira-work
        - write:jira-work
      environment:
        JIRA_CLIENT_ID: your-client-id
        JIRA_CLIENT_SECRET: your-client-secret

# Optional: define custom function behavior for ticket creation
functions:
  - name: create_jira_ticket
    description: Create a new JIRA ticket with project key, issue type, summary, and description.
    parameters:
      type: object
      properties:
        project_key:
          type: string
          description: JIRA project key (e.g., FRMT)
        issue_type:
          type: string
          description: Type of issue (e.g., Bug, Task, Story)
        summary:
          type: string
          description: Short title of the issue
        description:
          type: string
          description: Detailed description of the issue
        assignee:
          type: string
          description: Email or account ID of the assignee
        priority:
          type: string
          description: Priority (e.g., High, Medium, Low)
      required: [project_key, issue_type, summary, description]

permissions:
  - internet
  - read:user
  - read:repo
  - write:repo

environment_variables:
  JIRA_BASE_URL: https://your-domain.atlassian.net
  JIRA_EMAIL: your-email@company.com
  JIRA_API_TOKEN: your-api-token


---

⚙️ How It Works

tools: Defines JIRA’s REST API as an external OpenAPI spec.

auth: Uses OAuth2 (you can switch to Basic Auth if you prefer API tokens).

functions: Declares a high-level function create_jira_ticket that Copilot can call when a user says something like “Create a JIRA bug for the data aggregation issue.”

environment_variables: Store your JIRA credentials securely — Copilot reads these from your environment, not the YAML file directly.

permissions: Allow Copilot to reach the internet and use the JIRA API.



---

🧠 Example Usage (in Copilot)

You could say:

> “Create a JIRA ticket in project FRMT for a new bug — summary: Spark job fails on missing partition; description: job fails when no data found in table; assign to jasper@company.com, priority High.”



Copilot will then automatically call the create_jira_ticket function and send a POST request like:

POST /rest/api/3/issue
{
  "fields": {
    "project": { "key": "FRMT" },
    "issuetype": { "name": "Bug" },
    "summary": "Spark job fails on missing partition",
    "description": "Job fails when no data found in table",
    "assignee": { "emailAddress": "jasper@company.com" },
    "priority": { "name": "High" }
  }
}


---

Would you like me to adjust this YAML for JIRA Server (on-premise) instead of JIRA Cloud (Atlassian.net)? The auth and API URLs differ slightly.
