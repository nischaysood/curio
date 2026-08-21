package app.curio.data

/**
 * Bundled lesson content, byte-identical in shape to what /generate returns.
 *
 * Every fact here is checkable. A learning app that teaches something false is
 * worse than one that teaches nothing, and a cached course serves thousands of
 * people — so accuracy matters more than volume. Three short courses you trust
 * beat ten you haven't read.
 */

internal const val LEXICAL_ANALYSIS = """
{
  "lesson": "Lexical Analysis",
  "objective": "Turn a stream of characters into a stream of tokens.",
  "chunks": [
    { "shape": "definition", "concept": "token",
      "content": "A token is the smallest meaningful unit a compiler works with.",
      "distractors": ["A token is a single character of source code.", "A token is one line of a source file."] },
    { "shape": "definition", "concept": "lexeme",
      "content": "A lexeme is the exact run of characters in the source that produced a token.",
      "distractors": ["A lexeme is the type assigned to a variable.", "A lexeme is a compiler error message."] },
    { "shape": "definition", "concept": "lexer",
      "content": "A lexer is the component that scans characters and emits tokens.",
      "distractors": ["A lexer is the component that generates machine code.", "A lexer is the component that allocates memory."] },
    { "shape": "sequence", "concept": "tokenizing a line of source",
      "steps": ["Read the next character", "Decide which token class it could start", "Keep consuming while the characters still fit that class", "Emit the finished token", "Discard any whitespace before starting again"] },
    { "shape": "taxonomy", "concept": "token types",
      "categories": { "keyword": ["if", "while", "return"], "literal": ["42", "'a'", "3.14"], "operator": ["+", "==", "<<"] } },
    { "shape": "comparison", "concept": "what the lexer catches vs what the parser catches",
      "leftLabel": "Lexer", "rightLabel": "Parser",
      "items": { "An unterminated string literal": true, "An illegal character like a stray backtick": true, "A missing closing brace": false, "A function called with too few arguments": false } },
    { "shape": "fact", "concept": "whitespace handling",
      "question": "What does a typical lexer do with whitespace?",
      "answer": "Discards it, unless the language is indentation-sensitive",
      "distractors": ["Emits one token per space character", "Replaces it with a semicolon", "Passes it through to the code generator"] },
    { "shape": "concept", "concept": "lexical analysis",
      "content": "Lexical analysis converts a character stream into a token stream, throwing away detail the parser does not need.",
      "keyPoints": ["Input is raw characters, output is tokens", "It discards whitespace and comments", "It catches errors about characters, not about structure", "It runs before parsing and makes the parser's job much simpler"] }
  ]
}
"""

internal const val PARSING = """
{
  "lesson": "Parsing and Grammars",
  "objective": "Turn a flat list of tokens into a tree that captures structure.",
  "chunks": [
    { "shape": "definition", "concept": "parser",
      "content": "A parser is the component that builds a tree from a flat sequence of tokens.",
      "distractors": ["A parser is the component that removes whitespace.", "A parser is the component that assigns memory addresses."] },
    { "shape": "definition", "concept": "grammar",
      "content": "A grammar is the set of rules describing which token sequences are valid.",
      "distractors": ["A grammar is the list of keywords a language reserves.", "A grammar is the compiler's error message catalogue."] },
    { "shape": "definition", "concept": "parse tree",
      "content": "A parse tree is the nested structure showing how tokens combine into expressions and statements.",
      "distractors": ["A parse tree is the folder structure of a project.", "A parse tree is the order functions get called at run time."] },
    { "shape": "sequence", "concept": "parsing an expression",
      "steps": ["Take the next token", "Match it against the grammar rules that could apply", "Recursively parse the parts that rule expects", "Attach the finished node to its parent"] },
    { "shape": "comparison", "concept": "top-down vs bottom-up parsing",
      "leftLabel": "Top-down", "rightLabel": "Bottom-up",
      "items": { "Starts from the whole program and works inward": true, "Recursive descent is the classic example": true, "Builds small pieces first and combines them": false, "Used by most parser generator tools": false } },
    { "shape": "fact", "concept": "syntax errors",
      "question": "What does a parser do when tokens don't fit any grammar rule?",
      "answer": "Reports a syntax error describing what it expected",
      "distractors": ["Silently skips the statement", "Guesses the most likely correct code", "Passes the tokens to the optimiser anyway"] },
    { "shape": "concept", "concept": "parsing",
      "content": "Parsing checks that a token sequence matches the grammar and produces a tree that captures the program's structure.",
      "keyPoints": ["Input is tokens, output is a tree", "The grammar defines what counts as valid", "It catches structural errors, not spelling ones", "The tree is what every later stage operates on"] }
  ]
}
"""

internal const val CODE_GENERATION = """
{
  "lesson": "Code Generation",
  "objective": "Turn a checked tree into instructions a machine can run.",
  "chunks": [
    { "shape": "definition", "concept": "intermediate representation",
      "content": "An intermediate representation is a simplified form of the program that sits between the source tree and machine code.",
      "distractors": ["An intermediate representation is a partially compiled binary file.", "An intermediate representation is the parser's error log."] },
    { "shape": "definition", "concept": "register allocation",
      "content": "Register allocation is deciding which values live in the CPU's fast registers and which spill to memory.",
      "distractors": ["Register allocation is reserving disk space for the output binary.", "Register allocation is naming variables in the source."] },
    { "shape": "definition", "concept": "instruction selection",
      "content": "Instruction selection is choosing which machine instructions implement each operation.",
      "distractors": ["Instruction selection is picking which compiler flags to enable.", "Instruction selection is ordering functions in the source file."] },
    { "shape": "sequence", "concept": "the back end pipeline",
      "steps": ["Lower the tree into an intermediate representation", "Run optimisation passes over it", "Select machine instructions", "Allocate registers", "Emit the final object code"] },
    { "shape": "taxonomy", "concept": "optimisation kinds",
      "categories": { "local": ["constant folding", "dead code removal"], "loop": ["loop unrolling", "hoisting invariants"] } },
    { "shape": "fact", "concept": "constant folding",
      "question": "What does a compiler do with the expression 2 * 3 + 4?",
      "answer": "Computes 10 at compile time and emits the constant",
      "distractors": ["Emits three separate arithmetic instructions", "Defers it to the linker", "Warns that the expression is unused"] },
    { "shape": "concept", "concept": "code generation",
      "content": "Code generation lowers a checked tree into concrete machine instructions, deciding how abstract operations map onto real hardware.",
      "keyPoints": ["Input is a checked tree, output is machine code", "An intermediate representation makes optimisation tractable", "Registers are scarce, so allocation matters for speed", "The same program can compile to very different instructions per target"] }
  ]
}
"""

internal const val BIG_O_BASICS = """
{
  "lesson": "Big-O Notation",
  "objective": "Describe how an algorithm's cost grows as the input grows.",
  "chunks": [
    { "shape": "definition", "concept": "big-o notation",
      "content": "Big-O notation describes an upper bound on how an algorithm's cost grows as the input grows.",
      "distractors": ["Big-O notation measures how many seconds a program takes to run.", "Big-O notation counts how much memory a program allocates in total."] },
    { "shape": "definition", "concept": "constant time",
      "content": "Constant time means the cost does not change as the input grows.",
      "distractors": ["Constant time means the program always takes exactly one second.", "Constant time means the algorithm never uses a loop."] },
    { "shape": "definition", "concept": "amortised cost",
      "content": "Amortised cost is the average cost per operation across a long run of operations.",
      "distractors": ["Amortised cost is the cost of the slowest single operation.", "Amortised cost is the memory an algorithm frees on exit."] },
    { "shape": "sequence", "concept": "growth rates from fastest to slowest",
      "steps": ["O(1) constant", "O(log n) logarithmic", "O(n) linear", "O(n log n) linearithmic", "O(n squared) quadratic", "O(2 to the n) exponential"] },
    { "shape": "taxonomy", "concept": "common algorithm complexities",
      "categories": { "O(log n)": ["binary search", "balanced tree lookup"], "O(n log n)": ["merge sort", "heap sort"], "O(n squared)": ["bubble sort", "insertion sort"] } },
    { "shape": "comparison", "concept": "what big-o does and does not tell you",
      "leftLabel": "It tells you", "rightLabel": "It hides",
      "items": { "How cost scales with input size": true, "Which algorithm wins on huge inputs": true, "The constant factor": false, "Which is faster on ten items": false } },
    { "shape": "fact", "concept": "dropping constants",
      "question": "Why is O(2n) written as O(n)?",
      "answer": "Constant factors don't affect the growth rate",
      "distractors": ["Because doubling is always negligible", "Because n is assumed to be small", "Because the two operations run in parallel"] },
    { "shape": "concept", "concept": "big-o notation",
      "content": "Big-O describes how an algorithm's cost scales with input size, ignoring constants so you can compare approaches independently of hardware.",
      "keyPoints": ["It describes growth, not absolute speed", "Constants and lower-order terms are dropped", "It matters most as inputs get large", "A worse complexity can still win on small inputs"] }
  ]
}
"""

internal const val BIG_O_IN_PRACTICE = """
{
  "lesson": "Complexity in Practice",
  "objective": "Read an algorithm and work out its complexity yourself.",
  "chunks": [
    { "shape": "definition", "concept": "time complexity",
      "content": "Time complexity is how the number of operations grows with input size.",
      "distractors": ["Time complexity is how long a program runs on a given machine.", "Time complexity is the number of lines of code in an algorithm."] },
    { "shape": "definition", "concept": "space complexity",
      "content": "Space complexity is how the extra memory an algorithm needs grows with input size.",
      "distractors": ["Space complexity is the size of the compiled program.", "Space complexity is how much disk the input file occupies."] },
    { "shape": "definition", "concept": "worst case",
      "content": "The worst case is the input arrangement that makes an algorithm do the most work.",
      "distractors": ["The worst case is the largest input a program can accept.", "The worst case is what happens when the program crashes."] },
    { "shape": "sequence", "concept": "working out the complexity of a loop",
      "steps": ["Find the loops", "Work out how many times each one runs in terms of n", "Multiply the counts for nested loops", "Add the counts for sequential loops", "Drop constants and lower-order terms"] },
    { "shape": "comparison", "concept": "nested vs sequential loops",
      "leftLabel": "Multiply", "rightLabel": "Add",
      "items": { "A loop inside another loop": true, "Scanning a grid row by row": true, "Two loops one after the other": false, "Reading a list then sorting it": false } },
    { "shape": "fact", "concept": "complexity of nested loops",
      "question": "Two nested loops each running n times gives what complexity?",
      "answer": "O(n squared)",
      "distractors": ["O(2n)", "O(n log n)", "O(n)"] },
    { "shape": "concept", "concept": "analysing complexity",
      "content": "You work out complexity by counting how often the innermost work happens as a function of input size, then discarding everything that doesn't affect growth.",
      "keyPoints": ["Count operations in terms of n, not seconds", "Nested loops multiply, sequential loops add", "Drop constants and lower-order terms", "Worst case is the usual thing quoted"] }
  ]
}
"""

internal const val HTTPS_CERTIFICATES = """
{
  "lesson": "Certificates and Trust",
  "objective": "Understand why your browser believes a server is who it says it is.",
  "chunks": [
    { "shape": "definition", "concept": "public key",
      "content": "A public key is the half of a key pair you can share freely, used to encrypt or to verify signatures.",
      "distractors": ["A public key is the password shared between a browser and a server.", "A public key is the certificate a website displays."] },
    { "shape": "definition", "concept": "private key",
      "content": "A private key is the half of a key pair the server never shares, used to decrypt or to sign.",
      "distractors": ["A private key is the password an administrator uses to log in.", "A private key is the session key generated for each visit."] },
    { "shape": "definition", "concept": "chain of trust",
      "content": "A chain of trust is the sequence of signatures linking a site's certificate back to an authority your browser already trusts.",
      "distractors": ["A chain of trust is the list of sites you have previously visited.", "A chain of trust is the sequence of servers a request passes through."] },
    { "shape": "sequence", "concept": "verifying a certificate",
      "steps": ["Read the certificate the server presented", "Check that the domain matches the one you asked for", "Check it has not expired", "Follow its signature up to a root the browser trusts", "Check it has not been revoked"] },
    { "shape": "taxonomy", "concept": "certificate problems",
      "categories": { "browser blocks it": ["expired certificate", "wrong domain name", "untrusted issuer"], "browser allows it": ["short key by modern standards", "certificate issued very recently"] } },
    { "shape": "fact", "concept": "self-signed certificates",
      "question": "Why does a browser warn about a self-signed certificate?",
      "answer": "Nothing independent vouches for who owns the domain",
      "distractors": ["The connection is not encrypted", "The certificate uses weaker maths", "The site has been reported as malicious"] },
    { "shape": "concept", "concept": "certificate trust",
      "content": "Certificates work because a small set of authorities your browser already trusts sign for everyone else, turning an impossible problem into a manageable one.",
      "keyPoints": ["A key pair splits into one public half and one private half", "Certificates bind a public key to a domain name", "Trust is delegated from a small set of pre-installed roots", "A warning means identity is unproven, not that encryption failed"] }
  ]
}
"""

internal const val HTTPS_HANDSHAKE = """
{
  "lesson": "How HTTPS Works",
  "objective": "Understand how two strangers agree on a secret over an open network.",
  "chunks": [
    { "shape": "definition", "concept": "tls",
      "content": "TLS is the protocol that encrypts traffic between a browser and a server.",
      "distractors": ["TLS is the format used to store website passwords.", "TLS is the system that resolves domain names to addresses."] },
    { "shape": "definition", "concept": "certificate",
      "content": "A certificate is a signed document proving a server owns the domain it claims to.",
      "distractors": ["A certificate is the encryption key used for every message.", "A certificate is the log of who visited a website."] },
    { "shape": "definition", "concept": "certificate authority",
      "content": "A certificate authority is an organisation browsers trust to vouch for who owns a domain.",
      "distractors": ["A certificate authority is the server that stores your session cookies.", "A certificate authority is the government body that registers domains."] },
    { "shape": "sequence", "concept": "the TLS handshake",
      "steps": ["The browser says hello and lists the ciphers it supports", "The server replies with its certificate", "The browser verifies that certificate against a trusted authority", "Both sides derive a shared session key", "All further traffic is encrypted with that key"] },
    { "shape": "comparison", "concept": "what HTTPS protects and what it doesn't",
      "leftLabel": "Protected", "rightLabel": "Still visible",
      "items": { "The contents of the page you requested": true, "Passwords you type into a form": true, "Which domain you connected to": false, "Roughly how much data you transferred": false } },
    { "shape": "fact", "concept": "the padlock icon",
      "question": "What does the padlock in the address bar actually prove?",
      "answer": "The connection is encrypted and the certificate is valid",
      "distractors": ["The website is safe and trustworthy", "The company behind the site has been verified in person", "Your data will not be sold"] },
    { "shape": "concept", "concept": "https",
      "content": "HTTPS uses certificates to prove a server's identity, then derives a shared key so two parties who have never met can talk privately over an open network.",
      "keyPoints": ["Certificates prove identity, encryption provides privacy", "Trust is anchored in certificate authorities the browser already trusts", "A shared session key is derived, never sent in the clear", "It hides content but not which site you visited"] }
  ]
}
"""
