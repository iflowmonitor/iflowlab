/** Best-effort pretty printers for the output panel. Never throw — fall back to raw. */

export function prettyJson(raw: string): string {
  try {
    return JSON.stringify(JSON.parse(raw), null, 2);
  } catch {
    return raw;
  }
}

export function prettyXml(raw: string): string {
  try {
    const withBreaks = raw.replace(/>\s*</g, ">\n<").trim();
    const lines = withBreaks.split("\n");
    let indent = 0;
    const out: string[] = [];
    for (const line of lines) {
      const isClosing = /^<\//.test(line);
      const isSelfContained = /^<[^!?][^>]*\/>/.test(line) || /^<([\w:-]+)[^>]*>.*<\/\1>$/.test(line);
      const isOpening = /^<[^!?/][^>]*[^/]>$/.test(line) && !isSelfContained;
      if (isClosing) indent = Math.max(0, indent - 1);
      out.push("  ".repeat(indent) + line);
      if (isOpening) indent += 1;
    }
    return out.join("\n");
  } catch {
    return raw;
  }
}

export function renderBody(type: string, inline: string): string {
  switch (type) {
    case "JSON":
      return prettyJson(inline);
    case "XML":
      return prettyXml(inline);
    default:
      return inline;
  }
}
