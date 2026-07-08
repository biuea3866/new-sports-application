type ClientConfig = {
  id: string;
  name: string;
  description: string;
  configLanguage: string;
  configCode: string;
  notes: ReadonlyArray<string>;
};

const SERVER_URL_PLACEHOLDER = "https://<your-domain>/mcp/sse";
const TOKEN_PLACEHOLDER = "<your-mcp-token>";

const CLIENT_CONFIGS: ReadonlyArray<ClientConfig> = [
  {
    id: "claude-desktop",
    name: "Claude Desktop",
    description: "Anthropic Claude Desktop 앱에 MCP 서버를 연결합니다.",
    configLanguage: "json",
    configCode: `{
  "mcpServers": {
    "sports-mcp": {
      "url": "${SERVER_URL_PLACEHOLDER}",
      "headers": {
        "Authorization": "Bearer ${TOKEN_PLACEHOLDER}"
      }
    }
  }
}`,
    notes: [
      "설정 파일 경로: ~/Library/Application Support/Claude/claude_desktop_config.json (macOS)",
      "설정 변경 후 Claude Desktop을 완전히 종료하고 재시작해야 적용됩니다.",
      "Claude Desktop은 tool 실행 전 confirm 다이얼로그를 표시합니다. 자동 승인되지 않습니다.",
    ],
  },
  {
    id: "cursor",
    name: "Cursor",
    description: "Cursor IDE에 MCP 서버를 연결합니다.",
    configLanguage: "json",
    configCode: `{
  "mcpServers": {
    "sports-mcp": {
      "url": "${SERVER_URL_PLACEHOLDER}",
      "headers": {
        "Authorization": "Bearer ${TOKEN_PLACEHOLDER}"
      }
    }
  }
}`,
    notes: [
      "설정 파일 경로: ~/.cursor/mcp.json (전역) 또는 .cursor/mcp.json (프로젝트)",
      "Cursor 재시작 없이 설정 파일 저장만으로 MCP 서버가 갱신됩니다.",
      "Cursor는 tool 실행 시 사용자 승인 없이 자동 실행됩니다. tool 호출 범위를 반드시 확인하세요.",
    ],
  },
  {
    id: "continue-dev",
    name: "Continue.dev",
    description: "VS Code / JetBrains Continue 확장에 MCP 서버를 연결합니다.",
    configLanguage: "json",
    configCode: `{
  "experimental": {
    "modelContextProtocolServers": [
      {
        "transport": {
          "type": "sse",
          "url": "${SERVER_URL_PLACEHOLDER}",
          "requestOptions": {
            "headers": {
              "Authorization": "Bearer ${TOKEN_PLACEHOLDER}"
            }
          }
        }
      }
    ]
  }
}`,
    notes: [
      "설정 파일 경로: ~/.continue/config.json",
      "Continue.dev MCP 지원은 experimental 단계입니다. 기능이 변경될 수 있습니다.",
    ],
  },
  {
    id: "chatgpt",
    name: "ChatGPT (OpenAI Actions)",
    description: "ChatGPT Custom GPT Action 또는 ChatGPT Desktop에 MCP 서버를 연결합니다.",
    configLanguage: "json",
    configCode: `{
  "mcpServers": {
    "sports-mcp": {
      "type": "sse",
      "url": "${SERVER_URL_PLACEHOLDER}",
      "headers": {
        "Authorization": "Bearer ${TOKEN_PLACEHOLDER}"
      }
    }
  }
}`,
    notes: [
      "ChatGPT Desktop (macOS) 앱의 설정 > MCP 섹션에서 위 JSON을 입력합니다.",
      "ChatGPT MCP 지원은 2025년 기준 베타 단계입니다. 클라이언트 버전에 따라 동작이 다를 수 있습니다.",
    ],
  },
  {
    id: "n8n",
    name: "n8n",
    description: "n8n 워크플로우에서 MCP Client 노드로 서버를 연결합니다.",
    configLanguage: "yaml",
    configCode: `# n8n MCP Client 노드 설정
# Credentials → MCP Server → SSE 방식 선택
serverUrl: "${SERVER_URL_PLACEHOLDER}"
authentication: bearerToken
bearerToken: "${TOKEN_PLACEHOLDER}"`,
    notes: [
      "n8n의 MCP Client 노드는 n8n 1.64+ 에서 지원됩니다.",
      "Credentials 탭에서 'MCP Server' 자격증명을 생성하고 토큰을 입력합니다.",
      "n8n은 워크플로우 자동화 도구이므로 tool 호출이 사용자 확인 없이 자동 실행됩니다. 워크플로우 권한 설정을 반드시 검토하세요.",
    ],
  },
];

const EXTERNAL_LLM_LIMITATIONS = [
  {
    provider: "Claude Desktop / Claude.ai",
    limitations: [
      "tool 실행 전 사용자 confirm 다이얼로그가 반드시 표시됩니다. 자동 승인 옵션이 없습니다.",
      "대화 컨텍스트가 길어지면 tool 결과가 잘릴 수 있습니다 (컨텍스트 윈도우 한계).",
    ],
  },
  {
    provider: "ChatGPT",
    limitations: [
      "ChatGPT MCP 지원은 베타 단계로, tool 호출 실패 시 오류 메시지가 불명확할 수 있습니다.",
      "Custom GPT Action 방식은 SSE 스트리밍을 지원하지 않으며, 응답 지연이 발생할 수 있습니다.",
      "OpenAI 정책상 외부 서버 연결 시 데이터가 OpenAI 서버를 경유합니다.",
    ],
  },
  {
    provider: "Cursor / Continue.dev",
    limitations: [
      "IDE 내장 LLM 클라이언트는 tool 실행 시 사용자 확인 없이 자동 실행될 수 있습니다.",
      "MCP 오류 발생 시 IDE 콘솔에서 직접 로그를 확인해야 합니다.",
    ],
  },
  {
    provider: "n8n",
    limitations: [
      "워크플로우 자동화 특성상 tool이 사람의 개입 없이 연속 호출됩니다. Rate Limit 초과에 주의하세요.",
      "n8n 클라우드 버전은 아웃바운드 IP가 고정되지 않을 수 있습니다. 방화벽 설정 시 확인이 필요합니다.",
    ],
  },
];

function CodeBlock({
  code,
  language,
}: {
  code: string;
  language: string;
}): JSX.Element {
  return (
    <div className="relative rounded-md bg-gray-900">
      <span className="absolute right-3 top-2 text-xs text-gray-400">
        {language}
      </span>
      <pre className="overflow-x-auto p-4 pt-7 text-sm text-gray-100">
        <code>{code}</code>
      </pre>
    </div>
  );
}

function ClientSection({ config }: { config: ClientConfig }): JSX.Element {
  return (
    <section
      id={config.id}
      aria-labelledby={`heading-${config.id}`}
      className="rounded-lg border border-gray-200 p-6"
    >
      <h2
        id={`heading-${config.id}`}
        className="text-lg font-semibold text-gray-900"
      >
        {config.name}
      </h2>
      <p className="mt-1 text-sm text-gray-600">{config.description}</p>
      <div className="mt-4">
        <CodeBlock code={config.configCode} language={config.configLanguage} />
      </div>
      {config.notes.length > 0 && (
        <ul className="mt-3 space-y-1" aria-label={`${config.name} 유의사항`}>
          {config.notes.map((note) => (
            <li key={note} className="flex gap-2 text-sm text-gray-600">
              <span aria-hidden="true" className="mt-0.5 shrink-0 text-amber-500">
                !
              </span>
              {note}
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

export default function McpDocsPage(): JSX.Element {
  return (
    <div className="space-y-8">
      <header>
        <h1 className="text-2xl font-semibold text-gray-900">MCP 사용 가이드</h1>
        <p className="mt-1 text-sm text-gray-600">
          MCP 클라이언트별 토큰 등록 방법과 설정 예시입니다.
          토큰은{" "}
          <a
            href="/admin/mcp/tokens"
            className="text-blue-600 underline hover:text-blue-800 focus:outline-none focus:ring-2 focus:ring-blue-500"
          >
            MCP 토큰 관리
          </a>
          에서 발급하세요.
        </p>
      </header>

      <nav aria-label="클라이언트 섹션 바로가기">
        <ol className="flex flex-wrap gap-2">
          {CLIENT_CONFIGS.map((config) => (
            <li key={config.id}>
              <a
                href={`#${config.id}`}
                className="rounded-md border border-gray-300 px-3 py-1 text-sm hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-blue-500"
              >
                {config.name}
              </a>
            </li>
          ))}
        </ol>
      </nav>

      <section aria-label="클라이언트별 설정 가이드" className="space-y-6">
        {CLIENT_CONFIGS.map((config) => (
          <ClientSection key={config.id} config={config} />
        ))}
      </section>

      <section
        aria-labelledby="heading-limitations"
        className="rounded-lg border border-amber-200 bg-amber-50 p-6"
      >
        <h2
          id="heading-limitations"
          className="text-lg font-semibold text-amber-900"
        >
          외부 LLM 클라이언트 한계 고지
        </h2>
        <p className="mt-1 text-sm text-amber-800">
          외부 LLM 제공자(Claude, ChatGPT 등)를 통한 MCP 연결 시 아래 제약사항을 반드시 확인하세요.
        </p>
        <ul className="mt-4 space-y-4" aria-label="외부 LLM 한계 목록">
          {EXTERNAL_LLM_LIMITATIONS.map((item) => (
            <li key={item.provider}>
              <h3 className="text-sm font-semibold text-amber-900">
                {item.provider}
              </h3>
              <ul className="mt-1 space-y-1">
                {item.limitations.map((limitation) => (
                  <li
                    key={limitation}
                    className="flex gap-2 text-sm text-amber-800"
                  >
                    <span aria-hidden="true" className="mt-0.5 shrink-0">
                      &bull;
                    </span>
                    {limitation}
                  </li>
                ))}
              </ul>
            </li>
          ))}
        </ul>
      </section>
    </div>
  );
}
