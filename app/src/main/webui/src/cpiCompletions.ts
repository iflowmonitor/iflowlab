import type * as Monaco from "monaco-editor";

/**
 * Mock-API-aware Groovy completion (slice 6). Groovy is dynamically typed, so we
 * can't cheaply infer receiver types; instead we key member suggestions off the
 * receiver's name (message, messageLog/log, messageLogFactory, ITApiFactory, and
 * common names for the service mocks). Every suggested method mirrors a method the
 * workbench actually implements, so completions never lead to a NoSuchMethod at run.
 */

interface Member {
  label: string;
  insert: string; // snippet syntax
  detail: string;
  doc: string;
}

const MESSAGE: Member[] = [
  { label: "getBody", insert: "getBody(${1:String}.class)", detail: "<T> T getBody(Class<T>)", doc: "Body coerced to the given type (String, byte[], InputStream)." },
  { label: "setBody", insert: "setBody(${1:body})", detail: "void setBody(Object)", doc: "Replace the message body." },
  { label: "getHeader", insert: "getHeader(${1:name}, ${2:String}.class)", detail: "<T> T getHeader(String, Class<T>)", doc: "One header, coerced." },
  { label: "setHeader", insert: "setHeader(${1:name}, ${2:value})", detail: "void setHeader(String, Object)", doc: "Set a single header." },
  { label: "getHeaders", insert: "getHeaders()", detail: "Map<String,Object> getHeaders()", doc: "All headers." },
  { label: "setHeaders", insert: "setHeaders(${1:map})", detail: "void setHeaders(Map)", doc: "Replace all headers." },
  { label: "getProperty", insert: "getProperty(${1:name})", detail: "Object getProperty(String)", doc: "One exchange property." },
  { label: "setProperty", insert: "setProperty(${1:name}, ${2:value})", detail: "void setProperty(String, Object)", doc: "Set an exchange property." },
  { label: "getProperties", insert: "getProperties()", detail: "Map<String,Object> getProperties()", doc: "All exchange properties." },
  { label: "setProperties", insert: "setProperties(${1:map})", detail: "void setProperties(Map)", doc: "Replace all properties." },
  { label: "getBodySize", insert: "getBodySize()", detail: "long getBodySize()", doc: "Body size in bytes." },
  { label: "getAttachments", insert: "getAttachments()", detail: "Map<String,DataHandler> getAttachments()", doc: "Message attachments." },
  { label: "setAttachments", insert: "setAttachments(${1:map})", detail: "void setAttachments(Map)", doc: "Replace attachments." },
];

const MESSAGE_LOG: Member[] = [
  { label: "setStringProperty", insert: "setStringProperty(${1:name}, ${2:value})", detail: "void setStringProperty(String, String)", doc: "Attach a string property to the message-processing log." },
  { label: "addAttachmentAsString", insert: "addAttachmentAsString(${1:name}, ${2:payload}, ${3:'text/plain'})", detail: "void addAttachmentAsString(String, String, String)", doc: "Add a log attachment from a string." },
  { label: "addCustomHeaderProperty", insert: "addCustomHeaderProperty(${1:name}, ${2:value})", detail: "void addCustomHeaderProperty(String, String)", doc: "Add a custom header property (searchable in monitoring)." },
];

const LOG_FACTORY: Member[] = [
  { label: "getMessageLog", insert: "getMessageLog(${1:message})", detail: "MessageLog getMessageLog(Message)", doc: "Obtain the message-processing log for this message." },
];

const IT_API_FACTORY: Member[] = [
  { label: "getService", insert: "getService(${1:ValueMappingApi}.class, null)", detail: "<T> T getService(Class<T>, Object)", doc: "Resolve a CPI platform service." },
  { label: "getApi", insert: "getApi(${1:ValueMappingApi}.class, null)", detail: "<T> T getApi(Class<T>, Object)", doc: "Resolve a CPI platform service (alias of getService)." },
];

const VALUE_MAPPING: Member[] = [
  { label: "getMappedValue", insert: "getMappedValue(${1:srcAgency}, ${2:srcId}, ${3:srcValue}, ${4:tgtAgency}, ${5:tgtId})", detail: "String getMappedValue(String, String, String, String, String)", doc: "Look up a value mapping; null when unmapped." },
];

const SECURE_STORE: Member[] = [
  { label: "getUserCredential", insert: "getUserCredential(${1:alias})", detail: "UserCredential getUserCredential(String)", doc: "Credential deployed under the alias." },
];

const USER_CREDENTIAL: Member[] = [
  { label: "getUsername", insert: "getUsername()", detail: "String getUsername()", doc: "Credential username." },
  { label: "getPassword", insert: "getPassword()", detail: "char[] getPassword()", doc: "Credential password — wrap with new String(...)." },
];

function membersFor(receiver: string): Member[] {
  const r = receiver.toLowerCase();
  if (r === "message") return MESSAGE;
  if (r === "messagelogfactory") return LOG_FACTORY;
  if (r === "itapifactory") return IT_API_FACTORY;
  if (/log$/.test(r) || r.includes("messagelog")) return MESSAGE_LOG;
  if (r.includes("credential") || r === "cred") return USER_CREDENTIAL;
  if (r.includes("securestore") || r.includes("store")) return SECURE_STORE;
  if (r.includes("valuemap") || r === "vm" || r.includes("mapping")) return VALUE_MAPPING;
  return [];
}

const SNIPPETS: { label: string; insert: string; detail: string; doc: string }[] = [
  {
    label: "processData",
    detail: "CPI script entrypoint",
    doc: "The Groovy Script step entrypoint.",
    insert:
      "import com.sap.gateway.ip.core.customdev.util.Message\n\n" +
      "Message processData(Message message) {\n" +
      "\tdef body = message.getBody(String.class)\n" +
      "\t${0}\n" +
      "\treturn message\n" +
      "}",
  },
  {
    label: "valueMapping",
    detail: "resolve a value mapping",
    doc: "Look up a value mapping via ITApiFactory.",
    insert:
      "import com.sap.it.api.ITApiFactory\n" +
      "import com.sap.it.api.mapping.ValueMappingApi\n" +
      "def vm = ITApiFactory.getApi(ValueMappingApi.class, null)\n" +
      "def ${1:value} = vm.getMappedValue(${2:srcAgency}, ${3:srcId}, ${4:srcValue}, ${5:tgtAgency}, ${6:tgtId})",
  },
  {
    label: "getCredential",
    detail: "read a secure-store credential",
    doc: "Fetch a deployed User Credential.",
    insert:
      "import com.sap.it.api.ITApiFactory\n" +
      "import com.sap.it.api.securestore.SecureStoreService\n" +
      "def store = ITApiFactory.getService(SecureStoreService.class, null)\n" +
      "def cred = store.getUserCredential(${1:alias})\n" +
      "def user = cred.getUsername()\n" +
      "def pass = new String(cred.getPassword())",
  },
];

let registered = false;

export function registerCpiCompletions(monaco: typeof Monaco): void {
  if (registered) return;
  registered = true;

  monaco.languages.registerCompletionItemProvider("groovy", {
    triggerCharacters: ["."],
    provideCompletionItems(model, position) {
      const textUntil = model.getValueInRange({
        startLineNumber: position.lineNumber,
        startColumn: 1,
        endLineNumber: position.lineNumber,
        endColumn: position.column,
      });
      const word = model.getWordUntilPosition(position);
      const range: Monaco.IRange = {
        startLineNumber: position.lineNumber,
        endLineNumber: position.lineNumber,
        startColumn: word.startColumn,
        endColumn: word.endColumn,
      };

      const dot = /(\w+)\.\s*\w*$/.exec(textUntil);
      if (dot) {
        const members = membersFor(dot[1]);
        return {
          suggestions: members.map((m) => ({
            label: m.label,
            kind: monaco.languages.CompletionItemKind.Method,
            insertText: m.insert,
            insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
            detail: m.detail,
            documentation: m.doc,
            range,
          })),
        };
      }

      return {
        suggestions: SNIPPETS.map((s) => ({
          label: s.label,
          kind: monaco.languages.CompletionItemKind.Snippet,
          insertText: s.insert,
          insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet,
          detail: s.detail,
          documentation: s.doc,
          range,
        })),
      };
    },
  });
}
