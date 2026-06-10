import { marked } from 'marked'
import hljs from 'highlight.js'

marked.setOptions({
    highlight: function(code, lang) {
        if (lang && hljs.getLanguage(lang)) {
            try {
                return hljs.highlight(code, { language: lang }).value
            } catch (e) {
                console.error(e)
            }
        }
        return hljs.highlightAuto(code).value
    },
    breaks: true,
    gfm: true
})

const escapeHtml = (text) => {
    if (!text) return ''
    return text
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;')
}

export class StreamingMarkdownParser {
    constructor() {
        this.buffer = ''
        this.inCodeBlock = false
        this.codeLang = ''
        this.codeContent = ''
    }

    parse(chunk) {
        if (!chunk) return ''

        this.buffer += chunk
        let result = ''
        let i = 0

        while (i < this.buffer.length) {
            if (this.inCodeBlock) {
                const codeEnd = this.buffer.indexOf('```', i)
                if (codeEnd === -1) {
                    this.codeContent += this.buffer.substring(i)
                    this.buffer = ''
                    break
                } else {
                    this.codeContent += this.buffer.substring(i, codeEnd)
                    // 渲染完整的代码块
                    const validLang = this.codeLang && hljs.getLanguage(this.codeLang) ? this.codeLang : 'plaintext'
                    let highlighted = ''
                    try {
                        highlighted = hljs.highlight(this.codeContent, { language: validLang }).value
                    } catch (e) {
                        highlighted = escapeHtml(this.codeContent)
                    }
                    result += `<pre class="hljs"><code class="language-${validLang}">${highlighted}</code></pre>`

                    this.inCodeBlock = false
                    this.codeLang = ''
                    this.codeContent = ''
                    i = codeEnd + 3
                    this.buffer = this.buffer.substring(i)
                    i = 0
                }
            } else {
                const codeStart = this.buffer.indexOf('```', i)
                if (codeStart === -1) {
                    // 没有代码块，直接渲染普通文本
                    if (this.buffer.substring(i).trim()) {
                        result += marked.parse(this.buffer.substring(i))
                    }
                    this.buffer = ''
                    break
                } else {
                    // 渲染代码块前的文本
                    const beforeText = this.buffer.substring(i, codeStart)
                    if (beforeText.trim()) {
                        result += marked.parse(beforeText)
                    }

                    // 解析代码块语言
                    const afterBackticks = this.buffer.substring(codeStart + 3)
                    const langMatch = afterBackticks.match(/^(\w+)(?:\n|$)/)
                    if (langMatch) {
                        this.codeLang = langMatch[1]
                        i = codeStart + 3 + langMatch[0].length
                    } else {
                        this.codeLang = ''
                        i = codeStart + 3
                    }
                    this.inCodeBlock = true
                    this.buffer = this.buffer.substring(i)
                    i = 0
                }
            }
        }

        return result
    }

    flush() {
        let result = ''
        if (this.inCodeBlock && this.codeContent) {
            // 未完成的代码块，作为普通文本显示
            const codeText = '```' + (this.codeLang ? this.codeLang + '\n' : '\n') + this.codeContent
            result += marked.parse(codeText)
        }
        if (this.buffer.trim()) {
            result += marked.parse(this.buffer)
        }
        this.reset()
        return result
    }

    reset() {
        this.buffer = ''
        this.inCodeBlock = false
        this.codeLang = ''
        this.codeContent = ''
    }
}