<template>
  <el-dialog
      v-model="dialogVisible"
      title="AI生成提示词"
      width="600px"
      @close="handleClose"
  >
    <el-form :model="form" label-width="100px">
      <el-form-item label="需求描述">
        <el-input
            v-model="form.requirement"
            type="textarea"
            :rows="4"
            placeholder="请描述您需要的提示词功能，例如：你是一个专业的客服助手..."
        />
        <div class="form-tip">描述越详细，生成的提示词质量越高</div>
      </el-form-item>

      <el-form-item label="提示词类型">
        <el-select v-model="form.type" style="width: 100%">
          <el-option label="通用问答" value="CUSTOM" />
          <el-option label="RAG问答" value="RAG" />
          <el-option label="函数调用" value="FUNCTION_CALLING" />
          <el-option label="Agent决策" value="AGENT_DECISION" />
          <el-option label="Agent回答" value="AGENT_ANSWER" />
          <el-option label="内容总结" value="SUMMARY" />
        </el-select>
      </el-form-item>

      <el-form-item label="语言">
        <el-radio-group v-model="form.language">
          <el-radio value="zh-CN">中文</el-radio>
          <el-radio value="en-US">English</el-radio>
        </el-radio-group>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" @click="generate" :loading="generating">
        <el-icon><MagicStick /></el-icon> 生成提示词
      </el-button>
    </template>

    <!-- 生成结果 -->
    <div v-if="result.prompt" class="generate-result">
      <el-divider>生成结果</el-divider>
      <div class="result-header">
        <el-tag :type="getConfidenceType(result.confidenceScore)" size="large">
          置信度评分: {{ result.confidenceScore }} 分
        </el-tag>
      </div>
      <div class="result-section">
        <div class="section-title">
          <h4>提示词内容</h4>
          <el-button size="small" text @click="copyToClipboard(result.prompt)">
            <el-icon><CopyDocument /></el-icon> 复制
          </el-button>
        </div>
        <div class="prompt-content">
          <pre>{{ result.prompt || '暂无内容' }}</pre>
        </div>
      </div>
      <div class="result-actions">
        <el-button @click="result = { prompt: '' }">重新生成</el-button>
        <el-button type="primary" @click="apply">使用此提示词</el-button>
      </div>
    </div>
  </el-dialog>
</template>

<script setup>
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { MagicStick, CopyDocument } from '@element-plus/icons-vue'
import { promptApi } from '@/api'

const props = defineProps({
  modelValue: { type: Boolean, default: false }
})
const emit = defineEmits(['update:modelValue', 'apply'])

const dialogVisible = ref(false)
const generating = ref(false)
const form = ref({ requirement: '', type: 'CUSTOM', language: 'zh-CN' })
const result = ref({ prompt: '', confidenceScore: 0, suggestions: [] })

watch(() => props.modelValue, (val) => {
  dialogVisible.value = val
})
watch(dialogVisible, (val) => {
  emit('update:modelValue', val)
})

const handleClose = () => {
  form.value = { requirement: '', type: 'CUSTOM', language: 'zh-CN' }
  result.value = { prompt: '', confidenceScore: 0, suggestions: [] }
}

const generate = async () => {
  if (!form.value.requirement) {
    ElMessage.warning('请输入需求描述')
    return
  }
  generating.value = true
  try {
    const res = await promptApi.generate({
      requirement: form.value.requirement,
      type: form.value.type,
      language: form.value.language
    })
    if (res && res.code === 200 && res.data) {
      result.value = {
        prompt: res.data.prompt || res.data.userPromptTemplate || '',
        confidenceScore: res.data.confidenceScore || 80,
        suggestions: res.data.suggestions || []
      }
      ElMessage.success('生成成功')
    } else {
      ElMessage.error(res?.msg || '生成失败')
    }
  } catch (error) {
    ElMessage.error('生成失败：' + error.message)
  } finally {
    generating.value = false
  }
}

const apply = () => {
  if (result.value.prompt) {
    emit('apply', result.value.prompt)
    dialogVisible.value = false
  }
}

const copyToClipboard = async (text) => {
  if (!text) return
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('已复制到剪贴板')
  } catch (error) {
    ElMessage.error('复制失败')
  }
}

const getConfidenceType = (score) => {
  if (score >= 80) return 'success'
  if (score >= 60) return 'warning'
  return 'info'
}
</script>

<style scoped>
.generate-result {
  margin-top: 20px;
  max-height: 400px;
  overflow-y: auto;
}
.result-header {
  margin-bottom: 16px;
  text-align: center;
}
.result-section {
  margin-top: 16px;
}
.section-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.section-title h4 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: #e2e8f0;
}
.prompt-content {
  background: #0f1228;
  border: 1px solid #2a2f4a;
  border-radius: 10px;
  padding: 12px;
}
.prompt-content pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: 'SF Mono', Monaco, 'Fira Code', monospace;
  font-size: 12px;
  color: #cbd5e6;
}
.result-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 16px;
}
.form-tip {
  font-size: 12px;
  color: #64748b;
  margin-top: 4px;
}
</style>