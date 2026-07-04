// sample.groovy - ~50 lines exercising a for-loop, method calls, and closures.
// Line numbers matter: tests assert against specific lines in this file.
def total = 0
def items = ['alpha', 'beta', 'gamma', 'delta']
def lengths = []

for (int i = 0; i < items.size(); i++) {
    def item = items[i]
    def len = measure(item)
    lengths << len
    total += len
}

def doubled = lengths.collect { n -> n * 2 }

int running = 0
items.eachWithIndex { name, idx ->
    running += name.length()
    if (running > 10) {
        running = running - 1
    }
}

def summary = buildSummary(items, total)

// 'result' has no 'def' -> lands in the Binding (readable by the driver).
result = [
        total  : total,
        lengths: lengths,
        doubled: doubled,
        running: running,
        summary: summary,
]

int measure(String s) {
    int c = 0
    for (ch in s.toCharArray()) {
        c++
    }
    return c
}

String buildSummary(List<String> names, int total) {
    def sb = new StringBuilder()
    names.each { sb.append(it).append(';') }
    sb.append('total=').append(total)
    return sb.toString()
}
