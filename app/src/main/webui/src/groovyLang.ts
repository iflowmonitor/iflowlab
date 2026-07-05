import type * as Monaco from "monaco-editor";

/**
 * Registers a Groovy language with a Monarch tokenizer (R6, slice 1: highlighting
 * only). Adapted from Monaco's Java-style Monarch grammar with Groovy additions
 * (def, GString interpolation, triple-quoted strings, closures).
 */
export function registerGroovy(monaco: typeof Monaco): void {
  if (monaco.languages.getLanguages().some((l) => l.id === "groovy")) {
    return;
  }
  monaco.languages.register({ id: "groovy" });

  monaco.languages.setLanguageConfiguration("groovy", {
    comments: { lineComment: "//", blockComment: ["/*", "*/"] },
    brackets: [
      ["{", "}"],
      ["[", "]"],
      ["(", ")"],
    ],
    autoClosingPairs: [
      { open: "{", close: "}" },
      { open: "[", close: "]" },
      { open: "(", close: ")" },
      { open: '"', close: '"' },
      { open: "'", close: "'" },
    ],
    surroundingPairs: [
      { open: "{", close: "}" },
      { open: "[", close: "]" },
      { open: "(", close: ")" },
      { open: '"', close: '"' },
      { open: "'", close: "'" },
    ],
  });

  monaco.languages.setMonarchTokensProvider("groovy", {
    defaultToken: "",
    keywords: [
      "abstract", "as", "assert", "boolean", "break", "byte", "case", "catch",
      "char", "class", "const", "continue", "def", "default", "do", "double",
      "else", "enum", "extends", "final", "finally", "float", "for", "goto", "if",
      "implements", "import", "in", "instanceof", "int", "interface", "long",
      "native", "new", "package", "private", "protected", "public", "return",
      "short", "static", "strictfp", "super", "switch", "synchronized", "this",
      "threadsafe", "throw", "throws", "transient", "try", "void", "volatile",
      "while", "true", "false", "null", "var", "trait",
    ],
    operators: [
      "=", ">", "<", "!", "~", "?", ":", "==", "<=", ">=", "!=", "&&", "||",
      "++", "--", "+", "-", "*", "/", "&", "|", "^", "%", "<<", ">>", ">>>",
      "+=", "-=", "*=", "/=", "&=", "|=", "^=", "%=", "?:", "?.", "*.", "<=>",
      "==~", "=~",
    ],
    symbols: /[=><!~?:&|+\-*/^%]+/,
    escapes: /\\(?:[abfnrtv\\"'$]|x[0-9A-Fa-f]{1,4}|u[0-9A-Fa-f]{4})/,
    tokenizer: {
      root: [
        [/@[a-zA-Z_$][\w$]*/, "annotation"],
        [
          /[a-z_$][\w$]*/,
          { cases: { "@keywords": "keyword", "@default": "identifier" } },
        ],
        [/[A-Z][\w$]*/, "type.identifier"],
        { include: "@whitespace" },
        [/[{}()[\]]/, "@brackets"],
        [/@symbols/, { cases: { "@operators": "operator", "@default": "" } }],
        [/\d+\.\d+([eE][-+]?\d+)?[fFdD]?/, "number.float"],
        [/0[xX][0-9a-fA-F]+/, "number.hex"],
        [/\d+[lLgG]?/, "number"],
        [/[;,.]/, "delimiter"],
        [/"""/, { token: "string.quote", next: "@tripleString" }],
        [/'''/, { token: "string.quote", next: "@tripleStringSingle" }],
        [/"/, { token: "string.quote", next: "@dstring" }],
        [/'/, { token: "string.quote", next: "@sstring" }],
      ],
      whitespace: [
        [/[ \t\r\n]+/, "white"],
        [/\/\*/, "comment", "@comment"],
        [/\/\/.*$/, "comment"],
      ],
      comment: [
        [/[^/*]+/, "comment"],
        [/\*\//, "comment", "@pop"],
        [/[/*]/, "comment"],
      ],
      dstring: [
        [/\$\{/, { token: "delimiter.bracket", next: "@interp" }],
        [/\$[a-zA-Z_][\w.]*/, "variable"],
        [/[^\\"$]+/, "string"],
        [/@escapes/, "string.escape"],
        [/"/, { token: "string.quote", next: "@pop" }],
      ],
      sstring: [
        [/[^\\']+/, "string"],
        [/@escapes/, "string.escape"],
        [/'/, { token: "string.quote", next: "@pop" }],
      ],
      tripleString: [
        [/\$\{/, { token: "delimiter.bracket", next: "@interp" }],
        [/\$[a-zA-Z_][\w.]*/, "variable"],
        [/[^"$]+/, "string"],
        [/"""/, { token: "string.quote", next: "@pop" }],
        [/"/, "string"],
      ],
      tripleStringSingle: [
        [/[^']+/, "string"],
        [/'''/, { token: "string.quote", next: "@pop" }],
        [/'/, "string"],
      ],
      interp: [
        [/\}/, { token: "delimiter.bracket", next: "@pop" }],
        [/[a-z_$][\w$]*/, { cases: { "@keywords": "keyword", "@default": "identifier" } }],
        [/@symbols/, { cases: { "@operators": "operator", "@default": "" } }],
        [/[.,]/, "delimiter"],
      ],
    },
  });
}
