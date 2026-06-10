import { marked } from 'marked'
import hljs from 'highlight.js'

// 配置 marked
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

export class EnhancedStreamingMarkdownParser {
    constructor() {
        this.reset()
    }

    reset() {
        this.buffer = ''
        this.inCodeBlock = false
        this.codeLang = ''
        this.codeContent = ''
        this.lastRenderedLength = 0
        this.pendingText = ''
    }

    parse(chunk) {
        if (!chunk) return ''

        this.buffer += chunk
        let result = ''
        let i = 0

        while (i < this.buffer.length) {
            if (this.inCodeBlock) {
                // 在代码块中，查找结束标记
                const codeEnd = this.buffer.indexOf('```', i)
                if (codeEnd === -1) {
                    // 未找到结束标记，继续收集代码
                    this.codeContent += this.buffer.substring(i)
                    this.buffer = ''
                    // 实时渲染未完成的代码块（添加光标提示）
                    const validLang = this.codeLang && hljs.getLanguage(this.codeLang) ? this.codeLang : 'plaintext'
                    let highlighted = ''
                    try {
                        highlighted = hljs.highlight(this.codeContent, { language: validLang }).value
                    } catch (e) {
                        highlighted = escapeHtml(this.codeContent)
                    }
                    result = `<pre class="hljs"><code class="language-${validLang}">${highlighted}<span class="streaming-cursor">▊</span></code></pre>`
                    break
                } else {
                    // 找到结束标记，渲染完整代码块
                    this.codeContent += this.buffer.substring(i, codeEnd)
                    const validLang = this.codeLang && hljs.getLanguage(this.codeLang) ? this.codeLang : 'plaintext'
                    let highlighted = ''
                    try {
                        highlighted = hljs.highlight(this.codeContent, { language: validLang }).value
                    } catch (e) {
                        highlighted = escapeHtml(this.codeContent)
                    }
                    result += `<pre class="hljs"><code class="language-${validLang}">${highlighted}</code></pre>`

                    // 重置代码块状态
                    this.inCodeBlock = false
                    this.codeLang = ''
                    this.codeContent = ''
                    i = codeEnd + 3
                    this.buffer = this.buffer.substring(i)
                    i = 0
                }
            } else {
                // 不在代码块中，查找代码块开始
                const codeStart = this.buffer.indexOf('```', i)
                if (codeStart === -1) {
                    // 没有代码块，渲染剩余文本
                    const textToRender = this.buffer.substring(i)
                    if (textToRender.trim()) {
                        // 使用 marked 渲染普通文本
                        result += marked.parse(textToRender)
                    }
                    this.buffer = ''
                    break
                } else {
                    // 渲染代码块前的文本
                    const beforeText = this.buffer.substring(i, codeStart)
                    if (beforeText.trim()) {
                        result += marked.parse(beforeText)
                    }

                    // 开始新的代码块
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

        // 处理未完成的代码块
        if (this.inCodeBlock && this.codeContent) {
            // 将未完成的代码块作为普通代码块渲染（不等待结束标记）
            const validLang = this.codeLang && hljs.getLanguage(this.codeLang) ? this.codeLang : 'plaintext'
            let highlighted = ''
            try {
                highlighted = hljs.highlight(this.codeContent, { language: validLang }).value
            } catch (e) {
                highlighted = escapeHtml(this.codeContent)
            }
            result += `<pre class="hljs"><code class="language-${validLang}">${highlighted}</code></pre>`
        }

        // 处理剩余文本
        if (this.buffer.trim()) {
            result += marked.parse(this.buffer)
        }

        this.reset()
        return result
    }
}