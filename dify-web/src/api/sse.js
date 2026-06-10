/**
 * 根据业务意图获取合适的温度参数
 */
const getTemperatureByIntent = (intent) => {
    const temperatureMap = {
        'code': 0.3,
        'data': 0.2,
        'general': 0.7,
        'rag': 0.5,
        'creative': 0.9,
        'streaming': 0.7
    }
    return temperatureMap[intent] || 0.7
}

const fixFragmentedContent = (content) => {
    if (!content) return content

    let fixed = content

    // ============ 1. 修复代码块标记 ============
    // 修复被拆散的 ``` 符号
    fixed = fixed.replace(/`\s*``\s*python/g, '```python')
    fixed = fixed.replace(/```\s*python\s*/g, '```python\n')

    // ============ 2. 修复 Python import 语句 ============
    // "import requests4 import BeautifulSoup time" ->
    // "import requests\nfrom bs4 import BeautifulSoup\nimport time"
    fixed = fixed.replace(/import\s+(\w+)(\d+)\s+import\s+(\w+)\s+(\w+)/g,
        (match, pkg, num, module, next) => {
            return `import ${pkg}\nfrom bs${num} import ${module}\nimport ${next}`
        })

    // ============ 3. 深度修复代码块内容 ============
    const codeBlockRegex = /```python\n([\s\S]*?)```/g
    fixed = fixed.replace(codeBlockRegex, (match, code) => {
        let fixedCode = code

        // 3.1 修复 import 语句粘连
        fixedCode = fixedCode.replace(/requests(\d+)/g, 'requests\nimport bs$1')
        fixedCode = fixedCode.replace(/import\s+bs(\d+)\s+import/g, 'from bs$1 import')

        // 3.2 修复变量赋值断行
        fixedCode = fixedCode.replace(/headers\s*=\s*{\s*\n\s*User-Agent/g,
            'headers = {\n    "User-Agent"')
        fixedCode = fixedCode.replace(/url\s*=\s*\n\s*"/g, 'url = "')
        fixedCode = fixedCode.replace(/(\w+)\s*=\s*\n\s*"/g, '$1 = "')

        // 3.3 修复被拆散的字符串
        fixedCode = fixedCode.replace(/"Mozilla\/\s*\n\s*5\.0/g, '"Mozilla/5.0')
        fixedCode = fixedCode.replace(/NT\s+10\.\s*\n\s*0;/g, 'NT 10.0;')
        fixedCode = fixedCode.replace(/WebKit\/\s*\n\s*53/g, 'WebKit/53')
        fixedCode = fixedCode.replace(/Chrome\/\s*\n\s*120/g, 'Chrome/120')
        fixedCode = fixedCode.replace(/Safari\/\s*\n\s*537/g, 'Safari/537')

        // 3.4 修复 URL 断行
        fixedCode = fixedCode.replace(/movie\s*\n\s*\.douban/g, 'movie.douban')
        fixedCode = fixedCode.replace(/(\w+)\.\s*\n\s*(\w+)\.com/g, '$1.$2.com')

        // 3.5 修复方法调用断行
        fixedCode = fixedCode.replace(/requests\.\s*\n\s*get/g, 'requests.get')
        fixedCode = fixedCode.replace(/response\.\s*\n\s*raise/g, 'response.raise')
        fixedCode = fixedCode.replace(/soup\.\s*\n\s*find_all/g, 'soup.find_all')
        fixedCode = fixedCode.replace(/title_tag\.\s*\n\s*string/g, 'title_tag.string')

        // 3.6 修复异常处理断行
        fixedCode = fixedCode.replace(/except\s*\n\s*(\w+)/g, 'except $1')
        fixedCode = fixedCode.replace(/except\s+(\w+)\.\s*\n\s*(\w+)/g, 'except $1.$2')

        // 3.7 修复打印语句断行
        fixedCode = fixedCode.replace(/print\s*\n\s*\(/g, 'print(')
        fixedCode = fixedCode.replace(/print\s*\(\s*\n\s*f/g, 'print(f')

        // 3.8 修复注释断行
        fixedCode = fixedCode.replace(/#\s*\n\s*([^#\n]+)/g, '# $1')
        fixedCode = fixedCode.replace(/\)\s*\n\s*#/g, ')  #')

        // 3.9 修复参数断行
        fixedCode = fixedCode.replace(/headers\s*=\s*\n\s*headers/g, 'headers=headers')
        fixedCode = fixedCode.replace(/timeout\s*=\s*\n\s*10/g, 'timeout=10')
        fixedCode = fixedCode.replace(/class_\s*=\s*\n\s*"/g, 'class_="')

        // 3.10 修复列表和循环
        fixedCode = fixedCode.replace(/titles\s*=\s*\n\s*\[/g, 'titles = [')
        fixedCode = fixedCode.replace(/for\s+(\w+)\s+in\s+\n\s*(\w+)/g, 'for $1 in $2')
        fixedCode = fixedCode.replace(/enumerate\s*\n\s*\(/g, 'enumerate(')

        // 3.11 修复条件判断
        fixedCode = fixedCode.replace(/if\s+(\w+)\s+and\s+\n\s*(\w+)/g, 'if $1 and $2')
        fixedCode = fixedCode.replace(/not\s+\n\s*(\w+)/g, 'not $1')
        fixedCode = fixedCode.replace(/startswith\s*\n\s*\(/g, 'startswith(')

        // 3.12 清理多余空白
        fixedCode = fixedCode.replace(/\n{3,}/g, '\n\n')
        fixedCode = fixedCode.replace(/[ \t]+$/gm, '')

        return '```python\n' + fixedCode.trim() + '\n```'
    })

    // ============ 4. 修复代码块外的内容 ============
    // 修复段落中的异常换行
    fixed = fixed.replace(/([。，；：])\s*\n\s*([^\n])/g, '$1$2')

    // 修复 Markdown 语法
    fixed = fixed.replace(/\*\*\s*\n\s*([^*]+)\*\*/g, '**$1**')
    fixed = fixed.replace(/`\s*\n\s*([^`]+)\s*\n\s*`/g, '`$1`')

    // ============ 5. 确保代码块完整性 ============
    const openBlocks = (fixed.match(/```/g) || []).length
    if (openBlocks % 2 !== 0) {
        fixed += '\n```'
    }

    // 确保代码块前后有正确格式
    fixed = fixed.replace(/([^\n])```/g, '$1\n\n```')
    fixed = fixed.replace(/```(\w+)([^\n])/g, '```$1\n$2')

    return fixed
}

/**
 * 流式对话
 */
export async function streamChat(sessionId, message, configId, intent, onMessage, onError, onComplete, signal) {
    const token = localStorage.getItem('token')
    const temperature = getTemperatureByIntent(intent)

    try {
        const response = await fetch('/api/llm/stream/chat', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'token': token || ''
            },
            body: JSON.stringify({
                sessionId,
                message,
                configId,
                intent: intent,
                temperature,
                saveContext: true
            }),
            signal
        })

        if (!response.ok) {
            onError?.(new Error(`HTTP ${response.status}`))
            return
        }

        const reader = response.body.getReader()
        const decoder = new TextDecoder('utf-8')
        let buffer = ''
        let fullContent = ''

        while (true) {
            const { done, value } = await reader.read()
            if (done) {
                const finalContent = fixFragmentedContent(fullContent)
                onMessage?.({ content: finalContent })
                onComplete?.()
                break
            }

            buffer += decoder.decode(value, { stream: true })
            const lines = buffer.split('\n')
            buffer = lines.pop() || ''

            for (let line of lines) {
                line = line.trim()
                if (!line) continue

                let content = line
                if (line.startsWith('data:')) {
                    content = line.substring(5).trim()
                }

                if (content === '[DONE]') {
                    const finalContent = fixFragmentedContent(fullContent)
                    onMessage?.({ content: finalContent })
                    onComplete?.()
                    return
                }

                if (content && content !== '') {
                    fullContent += content

                    // 实时修复并发送
                    const fixedContent = fixFragmentedContent(fullContent)
                    onMessage?.({ content: fixedContent })
                }
            }
        }
    } catch (error) {
        if (error.name === 'AbortError') {
            console.log('Request aborted')
        } else {
            console.error('Stream error:', error)
            onError?.(error)
        }
    }
}