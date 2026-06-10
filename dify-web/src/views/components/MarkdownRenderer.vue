<template>
  <div class="markdown-renderer" v-html="renderedHtml"></div>
</template>

<script setup>
import { computed, watch, nextTick } from 'vue'
import { marked } from 'marked'
import hljs from 'highlight.js'
import 'highlight.js/styles/atom-one-dark.css'

const props = defineProps({
  content: {
    type: String,
    default: ''
  }
})

// ============ 修复 JSON 转义和格式问题 ============
const preprocessMarkdown = (text) => {
  if (!text) return ''
  let str = text

  // 1. 修复 JSON 转义后的换行符（将字面量 \n 替换为真正的换行）
  // 但要避免影响代码块内已经正确的换行
  str = str.replace(/\\n/g, '\n')

  // 2. 修复代码块语言标识
  // ```python 被拆成 \n```\npython 或 ```\npython
  str = str.replace(/```\s*\n\s*python/g, '```python')
  str = str.replace(/```\s*python/g, '```python')

  // 3. 修复代码块标记格式
  // 确保 ```python 前后有正确的换行
  str = str.replace(/([^\n])```python/g, '$1\n\n```python')
  str = str.replace(/```python([^\n])/g, '```python\n$1')

  // 4. 修复代码块结束标记
  str = str.replace(/```\s*$/gm, '```')

  // 5. 确保代码块完整性
  const codeBlockCount = (str.match(/```/g) || []).length
  if (codeBlockCount % 2 !== 0) {
    str += '\n```'
  }

  // 6. 清理多余的空行（但保留代码块前的空行）
  str = str.replace(/\n{3,}/g, '\n\n')

  return str
}

const renderer = new marked.Renderer()

renderer.code = function(code, language) {
  // 修复：如果语言是空或者是错误的，尝试检测
  let validLang = language
  if (!language || language === 'plaintext') {
    // 检测代码内容判断语言
    if (code.includes('import ') || code.includes('def ') || code.includes('class ')) {
      validLang = 'python'
    } else if (code.includes('function ') || code.includes('const ') || code.includes('let ')) {
      validLang = 'javascript'
    } else {
      validLang = 'plaintext'
    }
  }

  // 确保语言被 hljs 支持
  if (!hljs.getLanguage(validLang)) {
    validLang = 'plaintext'
  }

  // 清理代码开头的多余字符
  let cleanCode = code
  // 去掉开头的 "n" 字符（如果它是单独的）
  cleanCode = cleanCode.replace(/^n\n/, '\n')
  // 去掉开头的多余换行
  cleanCode = cleanCode.replace(/^\n+/, '')
  // 去掉结尾的多余换行
  cleanCode = cleanCode.replace(/\n+$/, '')

  let highlightedCode = cleanCode
  try {
    highlightedCode = hljs.highlight(cleanCode, {
      language: validLang,
      ignoreIllegals: true
    }).value
  } catch (e) {
    highlightedCode = cleanCode.replace(/</g, '&lt;').replace(/>/g, '&gt;')
  }

  const encodedCode = btoa(encodeURIComponent(cleanCode))

  return `
    <div class="code-block-wrapper">
      <div class="code-block-header">
        <span class="code-lang">${validLang}</span>
        <button class="copy-code-btn" data-code="${encodedCode}">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect>
            <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path>
          </svg>
          复制
        </button>
      </div>
      <pre><code class="hljs language-${validLang}">${highlightedCode}</code></pre>
    </div>
  `
}

renderer.codespan = (code) => `<code class="inline-code">${code}</code>`

marked.setOptions({
  renderer,
  breaks: true,
  gfm: true
})

const renderedHtml = computed(() => {
  if (!props.content) return ''

  try {
    const clean = preprocessMarkdown(props.content)
    return marked.parse(clean)
  } catch (e) {
    console.error('Markdown 渲染错误:', e)
    return `<pre>${escapeHtml(props.content)}</pre>`
  }
})


const escapeHtml = (text) => {
  const map = {
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#039;'
  }
  return text.replace(/[&<>"']/g, m => map[m])
}

// 绑定复制按钮事件
const bindCopyButtons = () => {
  document.querySelectorAll('.copy-code-btn:not([data-bound])').forEach(btn => {
    btn.setAttribute('data-bound', 'true')
    btn.onclick = async (e) => {
      e.preventDefault()
      e.stopPropagation()

      const encodedCode = btn.getAttribute('data-code')
      if (!encodedCode) return

      try {
        const code = decodeURIComponent(atob(encodedCode))
        await navigator.clipboard.writeText(code)

        const originalHtml = btn.innerHTML
        btn.innerHTML = `
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polyline points="20 6 9 17 4 12"></polyline>
          </svg>
          已复制
        `
        btn.style.color = '#4ade80'
        btn.style.borderColor = '#4ade80'

        setTimeout(() => {
          btn.innerHTML = originalHtml
          btn.style.color = ''
          btn.style.borderColor = ''
        }, 2000)
      } catch (err) {
        console.error('复制失败:', err)
      }
    }
  })
}

watch(renderedHtml, () => {
  nextTick(() => {
    bindCopyButtons()
  })
}, { immediate: true })
</script>

<style scoped>
.markdown-renderer {
  line-height: 1.6;
  color: #f8fafc;
}

.markdown-renderer :deep(.code-block-wrapper) {
  margin: 12px 0;
  border-radius: 8px;
  background: #0f1322;
  border: 1px solid #3a3f5c;
  overflow: hidden;
}

.markdown-renderer :deep(.code-block-header) {
  display: flex;
  justify-content: space-between;
  padding: 8px 12px;
  background: #0a0e1a;
  border-bottom: 1px solid #3a3f5c;
}

.markdown-renderer :deep(.code-lang) {
  font-size: 12px;
  color: #a5b4fc;
}

.markdown-renderer :deep(.copy-code-btn) {
  background: #1a1f3a;
  border: 1px solid #4a4f6e;
  color: #cbd5e6;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  cursor: pointer;
}

.markdown-renderer :deep(.copy-code-btn:hover) {
  background: #2d2f4a;
  border-color: #818cf8;
}

.markdown-renderer :deep(pre) {
  margin: 0;
  padding: 16px;
  overflow-x: auto;
  background: #0f1322;
}

.markdown-renderer :deep(code) {
  font-family: 'SF Mono', Monaco, 'Cascadia Code', monospace;
  font-size: 13px;
}
</style>