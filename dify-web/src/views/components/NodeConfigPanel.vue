<template>
  <div class="node-config-panel">
    <el-form ref="configFormRef" :model="localConfig" :rules="formRules" label-width="100px">
      <el-form-item label="节点名称">
        <el-input v-model="nodeName" @change="updateName" />
      </el-form-item>

      <!-- 开始节点配置 -->
      <template v-if="node.type === 'START'">
        <el-form-item label="输入变量">
          <div class="input-vars-editor">
            <div v-for="(inputVar, index) in localConfig.inputVariables || []" :key="index" class="input-var-row">
              <el-input v-model="inputVar.name" placeholder="变量名" size="small" style="width: 120px" />
              <el-select v-model="inputVar.type" placeholder="类型" size="small" style="width: 100px">
                <el-option label="字符串" value="string" />
                <el-option label="数字" value="number" />
                <el-option label="布尔值" value="boolean" />
                <el-option label="数组" value="array" />
                <el-option label="对象" value="object" />
              </el-select>
              <el-input v-model="inputVar.defaultValue" placeholder="默认值" size="small" style="width: 120px" />
              <el-button type="danger" size="small" @click="removeInputVar(index)" :icon="Delete" />
            </div>
            <el-button size="small" type="primary" plain @click="addInputVar">
              <el-icon><Plus /></el-icon> 添加输入变量
            </el-button>
          </div>
        </el-form-item>
      </template>

      <!-- 结束节点配置 -->
      <template v-if="node.type === 'END'">
        <el-divider content-position="left">输出配置</el-divider>

        <el-form-item label="输出变量">
          <div class="output-vars-editor">
            <div
                v-for="(outputVar, index) in localConfig.outputVariables || []"
                :key="index"
                class="output-var-row"
            >
              <el-input
                  v-model="outputVar.name"
                  placeholder="变量名（例如：result）"
                  size="small"
                  style="width: 150px"
              />

              <!-- 🔥 改成和 LLM/RAG 一样的下拉菜单选择变量 -->
              <div class="variable-selector" style="flex: 1; min-width: 280px;">
                <el-dropdown
                    trigger="click"
                    @command="(cmd) => setOutputVarSource(outputVar, cmd)"
                >
                  <div class="variable-selector-trigger">
                    <span v-if="outputVar.source" class="var-code">{{ outputVar.source }}</span>
                    <span v-else class="placeholder">选择变量</span>
                    <el-icon class="arrow-icon"><ArrowDown /></el-icon>
                  </div>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <!-- 节点输出变量 -->
                      <el-dropdown-item divided>
                        <strong>节点输出变量</strong>
                      </el-dropdown-item>
                      <el-dropdown-item
                          v-for="v in nodeOutputVars"
                          :key="`var.${v.name}`"
                          :command="`var.${v.name}`"
                      >
                        <span class="var-code">&#123;&#123;var.{{ v.name }}&#125;&#125;</span>
                        <el-tag size="small" type="info" style="margin-left: 8px">完整对象</el-tag>
                        <span class="var-desc"> - {{ v.fromNode }}</span>
                      </el-dropdown-item>

                      <!-- 如果没有节点输出变量 -->
                      <div v-if="nodeOutputVars.length === 0" class="empty-vars-tip">
                        <span style="color: #64748b; padding: 8px 16px; display: block;">暂无可用变量</span>
                      </div>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </div>

              <el-button
                  type="danger"
                  size="small"
                  @click="removeOutputVar(index)"
                  :icon="Delete"
              />
            </div>
            <el-button
                size="small"
                type="primary"
                plain
                @click="addOutputVar"
                style="margin-top: 8px"
            >
              <el-icon><Plus /></el-icon>
              添加输出变量
            </el-button>
          </div>
          <div class="form-tip">
            结束节点定义了工作流的最终输出结果，可以引用其他节点的输出变量
          </div>
        </el-form-item>
      </template>

      <!-- LLM节点配置 -->
      <template v-if="node.type === 'LLM'">
        <el-divider content-position="left">模型配置</el-divider>

        <el-form-item label="模型配置">
          <el-select
              v-model="localConfig.modelConfigId"
              placeholder="选择模型配置"
              filterable
              clearable
              @change="onModelConfigChange"
          >
            <el-option
                v-for="model in modelConfigList"
                :key="model.id"
                :label="`${model.configName} (${getModelTypeLabel(model.type)})`"
                :value="model.id"
            >
              <span style="float: left">{{ model.configName }}</span>
              <span style="float: right; color: #8492a6; font-size: 13px">
          {{ getModelTypeLabel(model.type) }}
        </span>
            </el-option>
          </el-select>
          <div class="form-tip">选择系统中已配置的模型</div>
        </el-form-item>

        <el-form-item label="模型详情" v-if="selectedModelDetail">
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="模型类型">
              {{ getModelTypeLabel(selectedModelDetail.type) }}
            </el-descriptions-item>
            <el-descriptions-item label="模型名称">
              {{ selectedModelDetail.modelName }}
            </el-descriptions-item>
            <el-descriptions-item label="Temperature">
              {{ selectedModelDetail.temperature }}
            </el-descriptions-item>
            <el-descriptions-item label="Max Tokens">
              {{ selectedModelDetail.maxTokens }}
            </el-descriptions-item>
          </el-descriptions>
        </el-form-item>

        <el-divider content-position="left">提示词配置</el-divider>

        <!-- 系统提示词 -->
        <el-form-item label="系统提示词">
          <div class="prompt-editor">
            <div class="prompt-toolbar">
              <el-button size="small" type="primary" plain @click="openSystemPromptGenerator">
                <el-icon><MagicStick /></el-icon>
                AI生成系统提示词
              </el-button>
              <div class="variable-toolbar">
                <el-button size="small" @click="clearSystemPrompt" plain>清空</el-button>
              </div>
            </div>
            <el-input
                v-model="localConfig.systemPrompt"
                type="textarea"
                :rows="4"
                placeholder="系统提示词：设定AI的角色、行为准则和输出格式"
                style="margin-top: 8px"
            />
            <div class="form-tip">系统提示词用于设定AI的角色和行为规范</div>
          </div>
        </el-form-item>

        <!-- 用户提示词 -->
        <el-form-item label="用户提示词">
          <div class="prompt-editor">
            <div class="prompt-toolbar">
              <div class="variable-toolbar">
                <el-dropdown @command="insertVariableToUser">
                  <el-button size="small" type="primary" plain>
                    插入变量 <el-icon><ArrowDown /></el-icon>
                  </el-button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item divided>
                        <strong>节点输出变量</strong>
                      </el-dropdown-item>
                      <el-dropdown-item
                          v-for="v in nodeOutputVars"
                          :key="v.name"
                          :command="`var.${v.name}`"
                      >
                        <span class="var-code">&#123;&#123;var.{{ v.name }}&#125;&#125;</span> - {{ v.fromNode }}
                      </el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
                <el-button size="small" @click="clearUserPrompt" plain>清空</el-button>
              </div>
            </div>
            <el-input
                v-model="localConfig.userPrompt"
                type="textarea"
                :rows="6"
                placeholder="用户提示词：具体的任务描述，可以引用变量，如 {{input.query}}"
                style="margin-top: 8px"
            />
            <div class="form-tip">用户提示词可以引用变量，如 &#123;&#123;input.query&#125;&#125;、&#123;&#123;var.xxx&#125;&#125;</div>
          </div>
        </el-form-item>

        <el-divider content-position="left">模型参数</el-divider>

        <el-form-item label="温度">
          <el-slider v-model="localConfig.temperature" :min="0" :max="2" :step="0.1" />
        </el-form-item>

        <el-divider content-position="left">输出配置</el-divider>

        <!-- 输出变量名和类型 -->
        <el-form-item label="输出变量名" >
          <el-input
              v-model="localConfig.outputVar"
              placeholder="例如: llm_response"
              style="width: 50%"
          />
          <el-select
              v-model="localConfig.outputType"
              placeholder="输出类型"
              size="small"
              style="width: 120px; margin-left: 8px"
              @change="onOutputTypeChange"
          >
            <el-option label="📝 字符串" value="string" />
            <el-option label="📦 JSON对象" value="json" />
            <el-option label="📋 数组" value="array" />
          </el-select>
          <div class="form-tip">
            设置后，其他节点可通过 <span class="var-code">&#123;&#123;var.{{ localConfig.outputVar || 'llm_response' }}&#125;&#125;</span> 引用此输出
          </div>
        </el-form-item>

        <!-- 数组类型配置 -->
        <template v-if="localConfig.outputType === 'array'">
          <el-form-item label="数组项类型">
            <el-radio-group v-model="localConfig.arrayItemType" @change="onArrayItemTypeChange">
              <el-radio value="string">字符串数组（每行一个值）</el-radio>
              <el-radio value="object">对象数组（JSON格式）</el-radio>
            </el-radio-group>
          </el-form-item>

          <!-- 字符串数组提示 -->
          <el-form-item v-if="localConfig.arrayItemType === 'string'">
            <el-alert type="info" :closable="false">
              <template #title>
                <span>输出格式说明</span>
              </template>
              <div>模型将以每行一个值的形式输出，系统会自动解析为数组</div>
              <div style="margin-top: 8px">
                <strong>示例输出：</strong>
                <pre style="margin: 4px 0; padding: 8px; background: #f5f7fa; border-radius: 4px">数据治理流程
数据清洗规则
数据融合方案</pre>
                <strong>解析结果：</strong>
                <code>["数据治理流程", "数据清洗规则", "数据融合方案"]</code>
              </div>
            </el-alert>

            <!-- 自动生成提示词 -->
            <el-form-item label="自动添加格式要求">
              <el-switch v-model="localConfig.autoFormatPrompt" />
              <div class="form-tip">开启后，系统会自动在提示词中添加输出格式要求</div>
            </el-form-item>

            <el-form-item v-if="localConfig.autoFormatPrompt">
              <div class="format-preview">
                <div class="format-preview-title">将自动添加到系统提示词的内容：</div>
                <pre>{{ arrayStringFormatPrompt }}</pre>
                <el-button size="small" type="primary" plain @click="appendFormatToSystemPrompt">
                  <el-icon><Plus /></el-icon> 追加到系统提示词
                </el-button>
              </div>
            </el-form-item>
          </el-form-item>

          <!-- 对象数组配置 -->
          <template v-if="localConfig.arrayItemType === 'object'">
            <el-form-item label="对象字段定义">
              <div class="output-fields-editor">
                <div v-for="(field, idx) in localConfig.outputFields" :key="idx" class="output-field-row">
                  <el-input v-model="field.name" placeholder="字段名" size="small" style="width: 120px" />
                  <el-select v-model="field.type" placeholder="类型" size="small" style="width: 100px">
                    <el-option label="字符串" value="string" />
                    <el-option label="数字" value="number" />
                    <el-option label="布尔值" value="boolean" />
                  </el-select>
                  <el-input v-model="field.description" placeholder="描述" size="small" style="width: 150px" />
                  <el-button type="danger" size="small" @click="removeOutputField(idx)" :icon="Delete" />
                </div>
                <el-button size="small" type="primary" plain @click="addOutputField">
                  <el-icon><Plus /></el-icon> 添加字段
                </el-button>
              </div>
            </el-form-item>

            <el-form-item>
              <el-alert type="info" :closable="false">
                <template #title>
                  <span>输出格式说明</span>
                </template>
                <div>模型将以JSON数组格式输出，系统会自动解析</div>
                <div style="margin-top: 8px">
                  <strong>示例输出：</strong>
                  <pre style="margin: 4px 0; padding: 8px; background: #f5f7fa; border-radius: 4px">[
  {"name": "数据治理", "description": "数据治理流程"},
  {"name": "数据清洗", "description": "数据清洗规则"}
]</pre>
                </div>
              </el-alert>
            </el-form-item>
          </template>
        </template>

        <!-- JSON对象类型配置 -->
        <template v-if="localConfig.outputType === 'json'">
          <el-form-item label="输出字段定义">
            <div class="output-fields-editor">
              <div v-for="(field, idx) in localConfig.outputFields" :key="idx" class="output-field-row">
                <el-input v-model="field.name" placeholder="字段名" size="small" style="width: 120px" />
                <el-select v-model="field.type" placeholder="类型" size="small" style="width: 100px">
                  <el-option label="字符串" value="string" />
                  <el-option label="数字" value="number" />
                  <el-option label="布尔值" value="boolean" />
                  <el-option label="数组" value="array" />
                </el-select>
                <el-input v-model="field.description" placeholder="描述" size="small" style="width: 150px" />
                <el-button type="danger" size="small" @click="removeOutputField(idx)" :icon="Delete" />
              </div>
              <el-button size="small" type="primary" plain @click="addOutputField">
                <el-icon><Plus /></el-icon> 添加字段
              </el-button>
            </div>
            <div class="form-tip">
              定义输出字段后，后续节点可通过 <code>&#123;&#123;var.{{ localConfig.outputVar }}.字段名&#125;&#125;</code> 引用具体字段
            </div>
          </el-form-item>

          <el-form-item>
            <el-alert type="info" :closable="false">
              <div><strong>示例输出：</strong></div>
              <pre style="margin: 4px 0; padding: 8px; background: #f5f7fa; border-radius: 4px">{
  "content": "回答内容",
  "summary": "摘要"
}</pre>
            </el-alert>
          </el-form-item>
        </template>

        <!-- 字符串类型提示 -->
        <template v-if="localConfig.outputType === 'string'">
          <el-form-item>
            <el-alert type="info" :closable="false">
              <div>模型将直接输出文本内容，后续节点可通过 <code>&#123;&#123;var.{{ localConfig.outputVar || 'llm_response' }}&#125;&#125;</code> 引用</div>
            </el-alert>
          </el-form-item>
        </template>
      </template>

      <!-- RAG节点配置 -->
      <template v-if="node.type === 'RAG'">
        <el-divider content-position="left">基础配置</el-divider>

        <el-form-item label="节点名称">
          <el-input v-model="nodeName" placeholder="请输入节点名称" @change="updateName" />
        </el-form-item>

        <!-- RAG节点配置 - 查询内容区域增强 -->
        <el-form-item label="查询内容">
          <div class="prompt-editor">
            <el-input
                v-model="localConfig.query"
                type="textarea"
                :rows="4"
                placeholder="请输入查询内容，支持变量，如：{{input.query}} 或 {{llm_response.content}}"
            />
            <div class="variable-toolbar" style="margin-top: 8px">
              <el-dropdown @command="insertQueryVariable">
                <el-button size="small" type="primary" plain>
                  <el-icon><ArrowDown /></el-icon>
                  插入变量
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item divided>
                      <strong>节点输出</strong>
                    </el-dropdown-item>
                    <template v-for="nodeVar in nodeOutputVarsWithFields" :key="nodeVar.outputVar">
                      <el-dropdown-item :command="`var.${nodeVar.outputVar}`" divided>
                        <span class="var-code">&#123;&#123;var.{{ nodeVar.outputVar }}&#125;&#125;</span>
                        <el-tag size="small" type="info" style="margin-left: 8px">完整对象</el-tag>
                        <span class="var-desc"> - {{ nodeVar.nodeName }}</span>
                      </el-dropdown-item>
                      <el-dropdown-item
                          v-for="field in nodeVar.allFields"
                          :key="`${nodeVar.outputVar}.${field.fullPath}`"
                          :command="`var.${nodeVar.outputVar}.${field.fullPath}`"
                          :divided="false"
                          :style="{ paddingLeft: '28px' }"
                      >
                        <span class="var-code">&#123;&#123;var.{{ nodeVar.outputVar }}.{{ field.fullPath }}&#125;&#125;</span>
                        <el-tag size="small" :type="getTypeTag(field.type)" style="margin-left: 8px">
                          {{ field.type }}
                        </el-tag>
                        <span class="var-desc"> - {{ field.description }}</span>
                      </el-dropdown-item>
                    </template>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
              <el-button size="small" @click="clearQuery" plain>清空</el-button>
            </div>
          </div>
          <div class="form-tip">
            查询内容支持变量替换，如 <code>&#123;&#123;input.query&#125;&#125;</code> 或 <code>&#123;&#123;var.llm_response.content&#125;&#125;</code>
          </div>
        </el-form-item>

        <el-divider content-position="left">检索配置</el-divider>

        <el-form-item label="知识库">
          <el-select
              v-model="localConfig.kbName"
              placeholder="请选择知识库（可选）"
              filterable
              clearable
              :loading="loadingKbList"
              @focus="loadKnowledgeBaseList"
              style="width: 100%"
          >
            <el-option
                v-for="kb in knowledgeBaseList"
                :key="kb.id || kb.name"
                :label="kb.name || kb.kbName"
                :value="kb.name || kb.kbName"
            >
              <span style="float: left">{{ kb.name || kb.kbName }}</span>
              <span style="float: right; color: #8492a6; font-size: 13px">
          {{ kb.description || '无描述' }}
        </span>
            </el-option>
          </el-select>
          <div class="form-tip">
            不选择则使用默认知识库。可在"知识库管理"中创建和管理知识库
          </div>
        </el-form-item>

        <el-form-item label="返回文档数量">
          <el-slider
              v-model="localConfig.topK"
              :min="1"
              :max="20"
              :step="1"
              show-stops
          />
          <div class="slider-value">返回 {{ localConfig.topK || 5 }} 个相关文档</div>
          <div class="form-tip">检索返回的相关文档数量，建议 3-10 个</div>
        </el-form-item>

        <el-form-item label="相似度阈值">
          <el-slider
              v-model="localConfig.threshold"
              :min="0"
              :max="1"
              :step="0.05"
              :format-tooltip="(val) => `${(val * 100).toFixed(0)}%`"
          />
          <div class="slider-value">阈值: {{ (localConfig.threshold || 0) * 100 }}%</div>
          <div class="form-tip">只返回相似度高于此值的文档，0 表示不限制</div>
        </el-form-item>

        <el-divider content-position="left">输出配置</el-divider>

        <el-form-item label="输出变量名">
          <el-input
              v-model="localConfig.outputVar"
              placeholder="例如: rag_documents"
              clearable
          >
            <template #append>
              <el-tag size="small" type="info">var.xxx</el-tag>
            </template>
          </el-input>
          <div class="form-tip">
            设置后，其他节点可通过 <code>&#123;&#123;var.{{ localConfig.outputVar || 'rag_documents' }}&#125;&#125;</code> 引用检索结果
          </div>
        </el-form-item>

        <template v-if="localConfig.outputVar">
          <el-divider content-position="left">输出说明</el-divider>
          <el-alert type="info" :closable="false">
            <template #title>
              <span>节点输出变量说明</span>
            </template>
            <div class="output-preview">
              <p><strong>{{ localConfig.outputVar }}</strong> 节点输出包含以下字段：</p>
              <ul>
                <li><code>documents</code> - 检索到的文档列表</li>
                <li><code>documentCount</code> - 文档数量</li>
                <li><code>context</code> - 拼接好的上下文字符串</li>
                <li><code>query</code> - 原始查询内容</li>
              </ul>
            </div>
          </el-alert>
        </template>

        <el-divider content-position="left">测试</el-divider>
        <el-form-item>
          <el-button type="primary" plain @click="testRagNode" :loading="testing">
            <el-icon><VideoPlay /></el-icon>
            测试检索
          </el-button>
          <el-button plain @click="clearTestResult" v-if="testResult">
            清除结果
          </el-button>
        </el-form-item>

        <div v-if="testResult" class="test-result">
          <el-alert
              :title="testResult.success ? '检索成功' : '检索失败'"
              :type="testResult.success ? 'success' : 'error'"
              :closable="false"
          />
          <div v-if="testResult.success" class="test-result-content">
            <div class="result-stats">
              <el-tag type="info">检索到 {{ testResult.documentCount }} 个文档</el-tag>
            </div>
            <div class="result-documents">
              <div
                  v-for="(doc, idx) in testResult.documents"
                  :key="idx"
                  class="result-document"
              >
                <div class="doc-header">
                  <span class="doc-index">文档 {{ idx + 1 }}</span>
                  <el-tag size="small" type="warning" v-if="doc.score">
                    相似度: {{ (doc.score * 100).toFixed(1) }}%
                  </el-tag>
                </div>
                <div class="doc-content">{{ doc.content?.substring(0, 200) }}...</div>
              </div>
            </div>
          </div>
          <div v-else class="test-error">
            <p>{{ testResult.error }}</p>
          </div>
        </div>
      </template>

      <!-- 函数节点配置 -->
      <template v-if="node.type === 'FUNCTION'">
        <el-divider content-position="left">基础配置</el-divider>

        <el-form-item label="函数名称">
          <el-select
              v-model="localConfig.functionName"
              placeholder="选择函数"
              filterable
              :loading="functionLoading"
              @focus="loadFunctionList"
          >
            <el-option
                v-for="func in functionList"
                :key="func.name"
                :label="`${func.name} - ${func.desc}`"
                :value="func.name"
            >
              <span style="float: left">{{ func.name }}</span>
              <span style="float: right; color: #8492a6; font-size: 12px">{{ func.desc?.substring(0, 30) }}</span>
            </el-option>
          </el-select>
          <div class="form-tip">选择要调用的函数工具</div>
        </el-form-item>

        <!-- 函数参数配置 -->
        <el-form-item label="参数配置">
          <div class="param-editor">
            <div class="param-toolbar" style="margin-bottom: 8px">
              <el-dropdown @command="insertParamVariable">
                <el-button size="small" type="primary" plain>
                  插入变量 <el-icon><ArrowDown /></el-icon>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item divided>
                      <strong>节点输出变量</strong>
                    </el-dropdown-item>
                    <el-dropdown-item
                        v-for="v in nodeOutputVars"
                        :key="v.name"
                        :command="`var.${v.name}`"
                    >
                      <span class="var-code">&#123;&#123;var.{{ v.name }}&#125;&#125;</span> - {{ v.fromNode }}
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
              <el-button size="small" @click="formatParametersJson" plain>格式化JSON</el-button>
              <el-button size="small" @click="clearParameters" plain>清空</el-button>
            </div>
            <el-input
                v-model="parametersJson"
                type="textarea"
                :rows="6"
                placeholder='{
  "query": "{{input.query}}",
  "limit": 10
}'
                @change="updateParameters"
            />
            <div class="form-tip">
              <div class="form-tip">
                参数值支持变量，如 <code>"query": "&#123;&#123;input.query&#125;&#125;"</code>
              </div>
            </div>
          </div>
        </el-form-item>

        <el-divider content-position="left">输出配置</el-divider>

        <el-form-item label="输出变量名" prop="outputVar">
          <el-input v-model="localConfig.outputVar" placeholder="例如: function_result" />
          <div class="form-tip">
            设置后，其他节点可通过 <span class="var-code">&#123;&#123;var.{{ localConfig.outputVar || 'function_result' }}&#125;&#125;</span> 引用函数执行结果
          </div>
        </el-form-item>

        <template v-if="localConfig.outputVar">
          <el-divider content-position="left">输出说明</el-divider>
          <el-alert type="info" :closable="false">
            <template #title>
              <span>节点输出变量说明</span>
            </template>
            <div class="output-preview">
              <p><strong>{{ localConfig.outputVar }}</strong> 节点输出包含以下字段：</p>
              <ul>
                <li><code>result</code> - 函数执行返回的结果</li>
                <li><code>success</code> - 执行是否成功</li>
              </ul>
            </div>
          </el-alert>
        </template>
      </template>

      <!-- Agent节点配置 -->
      <template v-if="node.type === 'AGENT'">
        <el-form-item label="Agent">
          <el-select
              v-model="localConfig.agentId"
              placeholder="选择Agent"
              filterable
              :loading="agentLoading"
          >
            <el-option
                v-for="agent in agentList"
                :key="agent.id"
                :label="agent.agentName || agent.name"
                :value="agent.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="查询内容">
          <el-input v-model="localConfig.query" placeholder="查询内容，支持变量" />
        </el-form-item>

        <el-form-item label="输出变量名">
          <el-input v-model="localConfig.outputVar" placeholder="输出变量名" />
        </el-form-item>
      </template>

      <!-- 条件节点配置 -->
      <template v-if="node.type === 'CONDITION'">
        <el-form-item label="条件表达式">
          <el-input v-model="localConfig.expression" placeholder="例如: {{llm_output.score}} > 0.8" />
        </el-form-item>
        <el-form-item>
          <el-alert
              title="提示"
              type="info"
              description="支持表达式，可以使用输入变量(input.xxx)和上下文变量(var.xxx)"
              :closable="false"
          />
        </el-form-item>
      </template>

      <!-- 代码节点配置 -->
      <template v-if="node.type === 'CODE'">
        <el-form-item label="语言">
          <el-select v-model="localConfig.language" placeholder="选择语言">
            <el-option label="JavaScript" value="javascript" />
          </el-select>
        </el-form-item>

        <el-form-item label="代码">
          <el-input
              v-model="localConfig.code"
              type="textarea"
              :rows="12"
              placeholder='// 在此编写JavaScript代码'
          />
        </el-form-item>

        <el-form-item label="输出变量名">
          <el-input v-model="localConfig.outputVar" placeholder="输出变量名" />
        </el-form-item>
      </template>
    </el-form>

    <div class="panel-actions">
      <el-button @click="$emit('close')">取消</el-button>
      <el-button type="primary" @click="saveConfig">保存</el-button>
    </div>

    <!-- ==================== 提示词生成器对话框 ==================== -->
    <el-dialog
        v-model="showPromptGeneratorDialog"
        title="AI生成提示词"
        width="600px"
        @close="resetGeneratePromptForm"
    >
      <el-form :model="generatePromptForm" label-width="100px">
        <el-form-item label="需求描述">
          <el-input
              v-model="generatePromptForm.requirement"
              type="textarea"
              :rows="4"
              placeholder="请描述您需要的提示词功能，例如：你是一个专业的客服助手..."
          />
          <div class="form-tip">描述越详细，生成的提示词质量越高</div>
        </el-form-item>

        <el-form-item label="提示词类型">
          <el-select v-model="generatePromptForm.type" style="width: 100%">
            <el-option label="通用问答" value="CUSTOM" />
            <el-option label="RAG问答" value="RAG" />
            <el-option label="函数调用" value="FUNCTION_CALLING" />
            <el-option label="Agent决策" value="AGENT_DECISION" />
            <el-option label="Agent回答" value="AGENT_ANSWER" />
            <el-option label="内容总结" value="SUMMARY" />
          </el-select>
        </el-form-item>

        <el-form-item label="语言">
          <el-radio-group v-model="generatePromptForm.language">
            <el-radio label="zh-CN">中文</el-radio>
            <el-radio label="en-US">English</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="showPromptGeneratorDialog = false">取消</el-button>
        <el-button type="primary" @click="doGeneratePrompt" :loading="generatingPrompt">
          <el-icon><MagicStick /></el-icon>
          生成提示词
        </el-button>
      </template>
    </el-dialog>

    <!-- ==================== 生成结果对话框 ==================== -->
    <!-- 生成结果对话框 -->
    <el-dialog
        v-model="showPromptResultDialog"
        title="生成的系统提示词"
        width="700px"
        top="5vh"
        @opened="onDialogOpened"
    >
      <div class="generate-result">
        <!-- 添加调试信息 -->
        <div class="result-header">
          <el-tag :type="getConfidenceType(generatedPromptResult.confidenceScore)" size="large">
            置信度评分: {{ generatedPromptResult.confidenceScore }} 分
          </el-tag>
        </div>

        <div class="result-section">
          <div class="section-title">
            <h4>提示词内容</h4>
            <el-button size="small" text @click="copyToClipboard(generatedPromptResult.prompt)">
              <el-icon><CopyDocument /></el-icon>
              复制
            </el-button>
          </div>
          <!-- 调试显示 - 检查是否正确显示 -->
          <div class="prompt-content">
            <!-- 直接显示原始数据用于调试 -->
            <div style="background: #f0f0f0; padding: 10px; margin-bottom: 10px;">
              调试信息:
              prompt长度 = {{ generatedPromptResult.prompt ? generatedPromptResult.prompt.length : 0 }}
            </div>
            <pre>{{ generatedPromptResult.prompt || '暂无内容' }}</pre>
          </div>
        </div>

        <div v-if="generatedPromptResult.modelParams && Object.keys(generatedPromptResult.modelParams).length > 0" class="result-section">
          <div class="section-title">
            <h4>推荐参数</h4>
          </div>
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="Temperature">
              {{ generatedPromptResult.modelParams.temperature ?? '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="Max Tokens">
              {{ generatedPromptResult.modelParams.maxTokens ?? '-' }}
            </el-descriptions-item>
          </el-descriptions>
        </div>

        <div v-if="generatedPromptResult.suggestions && generatedPromptResult.suggestions.length > 0" class="result-section">
          <div class="section-title">
            <h4>优化建议</h4>
          </div>
          <ul class="suggestions-list">
            <li v-for="(suggestion, idx) in generatedPromptResult.suggestions" :key="idx">
              <el-icon><Check /></el-icon>
              {{ suggestion }}
            </li>
          </ul>
        </div>
      </div>

      <template #footer>
        <el-button @click="showPromptResultDialog = false">取消</el-button>
        <el-button @click="appendGeneratedPrompt">追加到现有提示词</el-button>
        <el-button type="primary" @click="useGeneratedPrompt">替换现有提示词</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, watch, onMounted, inject, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { ArrowDown, Delete, Plus, MagicStick, CopyDocument, Check, VideoPlay } from '@element-plus/icons-vue'
import { functionApi, agentApi, ragApi, promptApi } from '@/api'

// ==================== 提示词生成相关 ====================
const showPromptGeneratorDialog = ref(false)
const showPromptResultDialog = ref(false)
const generatingPrompt = ref(false)

const generatePromptForm = ref({
  requirement: '',
  type: 'CUSTOM',
  language: 'zh-CN'
})

// 添加打开对话框时的回调
const onDialogOpened = () => {
  // 强制刷新视图
  console.log('对话框打开，当前 prompt:', generatedPromptResult.value.prompt)
  // 如果数据为空，重新设置
  if (!generatedPromptResult.value.prompt) {
    console.warn('prompt 数据为空')
  }
}
onMounted(() => {
  console.log('=== RAG 节点调试 ===')
  console.log('nodeOutputVars:', nodeOutputVars)
  console.log('nodeOutputVars 类型:', typeof nodeOutputVars)
  console.log('nodeOutputVars 是否为数组:', Array.isArray(nodeOutputVars))
  if (nodeOutputVars && nodeOutputVars.length > 0) {
    console.log('第一个节点输出:', nodeOutputVars[0])
  }
})
// 添加输出字段
const addOutputField = () => {
  if (!localConfig.outputFields) localConfig.outputFields = []
  localConfig.outputFields.push({ name: '', type: 'string', description: '' })
}

// 移除输出字段
const removeOutputField = (index) => {
  localConfig.outputFields.splice(index, 1)
}

// 输出类型变化
const onOutputTypeChange = (type) => {
  if (type === 'array') {
    if (!localConfig.arrayItemType) localConfig.arrayItemType = 'string'
    if (!localConfig.outputFields) localConfig.outputFields = []
  } else if (type === 'json') {
    if (!localConfig.outputFields) localConfig.outputFields = []
  }
}

const configFormRef = ref(null)

// 数组项类型变化
const onArrayItemTypeChange = (type) => {
  if (type === 'object' && (!localConfig.outputFields || localConfig.outputFields.length === 0)) {
    localConfig.outputFields = [
      { name: 'name', type: 'string', description: '名称' },
      { name: 'value', type: 'string', description: '值' }
    ]
  }
}

// 字符串数组格式提示词
const arrayStringFormatPrompt = computed(() => {
  return `【输出格式要求】
请按以下格式输出，每行一个值，不要添加编号、序号或任何额外说明：

值1
值2
值3

示例输出：
数据治理流程
数据清洗规则
数据融合方案`
})
// 追加格式要求到系统提示词
const appendFormatToSystemPrompt = () => {
  const currentPrompt = localConfig.systemPrompt || ''
  const formatPrompt = arrayStringFormatPrompt.value
  if (!currentPrompt.includes('【输出格式要求】')) {
    localConfig.systemPrompt = currentPrompt + '\n\n' + formatPrompt
    ElMessage.success('已添加到系统提示词')
  } else {
    ElMessage.warning('系统提示词已包含格式要求')
  }
}

// 构建带字段信息的节点输出变量列表
// 构建带字段信息的节点输出变量列表
const nodeOutputVarsWithFields = computed(() => {
  // 🔥 关键修复：获取 computed 的实际值
  const vars = nodeOutputVars?.value || []

  if (!vars || !Array.isArray(vars)) return []

  return vars.map(varInfo => {
    let fields = []

    const nodeType = varInfo.nodeType || varInfo.type

    if (nodeType === 'LLM') {
      const nodeConfig = varInfo.config || varInfo.data?.config || {}
      const outputType = nodeConfig.outputType || varInfo.outputType || 'string'

      if (outputType === 'array') {
        // 🔥 数组类型：不生成子字段，直接使用整个数组
        const itemType = nodeConfig.arrayItemType || 'string'
        fields = [{
          name: '',
          type: 'array',
          description: itemType === 'string' ? '字符串数组，可直接使用' : '对象数组',
          fullPath: '',
          depth: 0,
          isArray: true
        }]
      } else if (outputType === 'json' && nodeConfig.outputFields && nodeConfig.outputFields.length > 0) {
        fields = nodeConfig.outputFields.map(f => ({
          name: f.name,
          type: f.type,
          description: f.description || '',
          fullPath: f.name,
          depth: 0
        }))
      } else {
        // 字符串类型
        fields = [
          { name: 'content', type: 'string', description: 'LLM生成的文本内容', fullPath: 'content', depth: 0 }
        ]
      }
    } else if (nodeType === 'RAG') {
      fields = [
        { name: 'context', type: 'string', description: '检索到的知识库内容', fullPath: 'context', depth: 0 },
        { name: 'documents', type: 'array', description: '文档列表', fullPath: 'documents', depth: 0 },
        { name: 'documentCount', type: 'number', description: '文档数量', fullPath: 'documentCount', depth: 0 },
        { name: 'query', type: 'string', description: '查询内容', fullPath: 'query', depth: 0 }
      ]
    } else if (nodeType === 'FUNCTION') {
      fields = [
        { name: 'result', type: 'any', description: '函数执行结果', fullPath: 'result', depth: 0 }
      ]
    } else if (nodeType === 'AGENT') {
      fields = [
        { name: 'result', type: 'string', description: 'Agent执行结果', fullPath: 'result', depth: 0 }
      ]
    } else if (nodeType === 'CODE') {
      fields = [
        { name: 'result', type: 'any', description: '代码执行结果', fullPath: 'result', depth: 0 }
      ]
    } else if (nodeType === 'START') {
      // 🔥 START 节点：不生成子字段，直接返回空数组
      fields = []
    } else {
      // 其他未知节点类型，不生成子字段
      fields = []
    }

    return {
      nodeId: varInfo.nodeId || varInfo.id,
      nodeName: varInfo.fromNode || varInfo.nodeName || varInfo.name,
      nodeType: nodeType,
      outputVar: varInfo.name || varInfo.outputVar,
      outputType: varInfo.config?.outputType || varInfo.outputType || 'object',
      allFields: fields.filter(f => f.name !== '')
    }
  })
})

// 获取类型标签样式
const getTypeTag = (type) => {
  const typeMap = {
    'string': '',
    'number': 'success',
    'boolean': 'warning',
    'array': 'danger',
    'object': 'info',
    'any': 'info'
  }
  return typeMap[type] || 'info'
}

// 插入字段到查询内容
const insertFieldToQuery = (varPath) => {
  const variable = `{{${varPath}}}`
  localConfig.query = (localConfig.query || '') + variable
}

const generatedPromptResult = ref({
  prompt: '',
  systemPrompt: '',
  modelParams: {},
  confidenceScore: 0,
  suggestions: []
})
// 当前正在编辑的提示词类型（system 或 user）
const currentPromptType = ref('user')

// 打开系统提示词生成器
const openSystemPromptGenerator = () => {
  if (localConfig.systemPrompt) {
    generatePromptForm.value.requirement = localConfig.systemPrompt
  } else {
    generatePromptForm.value.requirement = ''
  }
  showPromptGeneratorDialog.value = true
}

// 插入变量到用户提示词
const insertVariableToUser = (varPath) => {
  const variable = `{{${varPath}}}`
  localConfig.userPrompt = (localConfig.userPrompt || '') + variable
}

// 清空系统提示词
const clearSystemPrompt = () => {
  localConfig.systemPrompt = ''
}

// 清空用户提示词
const clearUserPrompt = () => {
  localConfig.userPrompt = ''
}

const doGeneratePrompt = async () => {
  if (!generatePromptForm.value.requirement) {
    ElMessage.warning('请输入需求描述')
    return
  }

  generatingPrompt.value = true
  try {
    const res = await promptApi.generate({
      requirement: generatePromptForm.value.requirement,
      type: generatePromptForm.value.type,
      language: generatePromptForm.value.language
    })

    if (res && res.code === 200 && res.data) {
      const data = res.data

      // 逐个字段赋值，而不是整体替换
      generatedPromptResult.value.prompt = data.prompt || data.userPromptTemplate || ''
      generatedPromptResult.value.systemPrompt = data.systemPrompt || ''
      generatedPromptResult.value.userPromptTemplate = data.userPromptTemplate || ''
      generatedPromptResult.value.modelParams = data.modelParams || {}
      generatedPromptResult.value.confidenceScore = data.confidenceScore || 80
      generatedPromptResult.value.suggestions = data.suggestions || []

      console.log('prompt 值:', generatedPromptResult.value.prompt)

      showPromptGeneratorDialog.value = false
      showPromptResultDialog.value = true
      ElMessage.success('生成成功')
    } else {
      ElMessage.error(res?.msg || '生成失败')
    }
  } catch (error) {
    console.error('生成提示词失败:', error)
    ElMessage.error('生成失败：' + (error.message || '未知错误'))
  } finally {
    generatingPrompt.value = false
  }
}

const useGeneratedPrompt = () => {
  if (generatedPromptResult.value.prompt) {
    localConfig.systemPrompt = generatedPromptResult.value.prompt
    if (generatedPromptResult.value.modelParams?.temperature !== undefined) {
      localConfig.temperature = generatedPromptResult.value.modelParams.temperature
    }
    ElMessage.success('系统提示词已应用')
    showPromptResultDialog.value = false
  }
}

const appendGeneratedPrompt = () => {
  if (generatedPromptResult.value.prompt) {
    const currentPrompt = localConfig.systemPrompt || ''
    localConfig.systemPrompt = currentPrompt + '\n\n' + generatedPromptResult.value.prompt
    ElMessage.success('系统提示词已追加')
    showPromptResultDialog.value = false
  }
}

const resetGeneratePromptForm = () => {
  generatePromptForm.value = {
    requirement: '',
    type: 'CUSTOM',
    language: 'zh-CN'
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

// ==================== RAG 节点相关 ====================
const knowledgeBaseList = ref([])
const loadingKbList = ref(false)
const testing = ref(false)
const testResult = ref(null)

const loadKnowledgeBaseList = async () => {
  if (knowledgeBaseList.value.length > 0) return
  loadingKbList.value = true
  try {
    const res = await ragApi.getKnowledgeBases()
    if (res.code === 200 && res.data) {
      knowledgeBaseList.value = res.data
    }
  } catch (error) {
    console.error('加载知识库列表失败:', error)
    ElMessage.error('加载知识库列表失败')
  } finally {
    loadingKbList.value = false
  }
}

const insertQueryVariable = (varPath) => {
  const variable = `{{${varPath}}}`
  const currentQuery = localConfig.query || ''
  localConfig.query = currentQuery + variable
}

const clearQuery = () => {
  localConfig.query = ''
}

const testRagNode = async () => {
  if (!localConfig.query) {
    ElMessage.warning('请输入查询内容')
    return
  }

  // 确保 topK 有效
  let topK = localConfig.topK
  if (!topK || topK < 1) {
    topK = 5
  }
  console.log('topK (处理后):', topK)

  try {
    const query = localConfig.query
    const kbName = localConfig.kbName

    const res = await ragApi.searchDocument({
      kb: kbName,
      query: query,
      topK: topK
    })

    if (res.code === 200) {
      const data = res.data
      let documents = []

      if (data.details && Array.isArray(data.details)) {
        documents = data.details.map(doc => ({
          content: doc.document || doc.content || '',
          score: doc.score
        }))
      }

      testResult.value = {
        success: true,
        documentCount: documents.length,
        documents: documents
      }
      ElMessage.success(`检索成功，找到 ${documents.length} 个相关文档`)
    } else {
      testResult.value = {
        success: false,
        error: res.msg || '检索失败'
      }
      ElMessage.error(res.msg || '检索失败')
    }
  } catch (error) {
    testResult.value = {
      success: false,
      error: error.message
    }
    ElMessage.error('测试失败: ' + error.message)
  } finally {
    testing.value = false
  }
}

const clearTestResult = () => {
  testResult.value = null
}

const props = defineProps({
  node: {
    type: Object,
    required: true
  }
})

const emit = defineEmits(['update', 'close'])

const inputVarList = inject('inputVarList', [
  { name: 'query', description: '用户问题' },
  { name: 'userId', description: '用户ID' },
  { name: 'sessionId', description: '会话ID' }
])

const nodeOutputVars = inject('nodeOutputVars', [])

const modelConfigList = ref([])
const selectedModelDetail = ref(null)
//后面改
const loadModelConfigs = async () => {
  try {
    // const res = await modelConfigApi.getEnabledConfigs()
    const res = ''
    console.log('模型配置API响应:', res)
    if (res.code === 200 && res.data) {
      modelConfigList.value = res.data

      // 🔥 修复：如果有数据，默认选中第一个模型
      if (res.data.length > 0 && !localConfig.modelConfigId) {
        localConfig.modelConfigId = res.data[0].id
        // 同步更新模型详情
        selectedModelDetail.value = res.data[0]
      }
    }
  } catch (error) {
    console.error('加载模型配置失败:', error)
    ElMessage.error('加载模型配置失败，请检查接口')
  }
}

const getModelTypeLabel = (type) => {
  const labels = {
    modelScope: 'ModelScope',
    openai: 'OpenAI',
    ollama: 'Ollama',
    qwen: '通义千问',
    ernie: '文心一言',
    spark: '讯飞星火',
    zhipu: '智谱AI'
  }
  return labels[type] || type
}

const onModelConfigChange = (configId) => {
  if (configId) {
    const model = modelConfigList.value.find(m => m.id === configId)
    selectedModelDetail.value = model
  } else {
    selectedModelDetail.value = null
  }
}

const nodeName = computed({
  get: () => props.node.name,
  set: (val) => {
    emit('update', { name: val })
  }
})

const localConfig = reactive({
  // LLM 节点配置
  modelConfigId: null,
  modelType: null,
  modelName: null,
  systemPrompt: '',
  userPrompt: '',
  temperature: 0.7,
  outputVar: '',
  outputType: 'string',
  arrayItemType: 'string',
  outputFields: [],
  autoFormatPrompt: false,

  // RAG 节点配置
  query: '',           // 🔥 确保有默认值
  kbName: '',
  topK: 5,
  threshold: 0.5,

  // 函数节点配置
  functionName: '',
  parameters: {},

  // Agent 节点配置
  agentId: null,

  // 条件节点配置
  expression: '',

  // 代码节点配置
  code: '',
  language: 'javascript',

  // 开始/结束节点配置
  inputVariables: [],
  outputVariables: []
})

const functionList = ref([])
const agentList = ref([])
const functionLoading = ref(false)
const agentLoading = ref(false)
const parametersJson = ref('{}')

// 插入变量到参数（原有方法）
const insertParamVariable = (varPath) => {
  const variable = `"{{${varPath}}}"`
  const currentText = parametersJson.value || ''
  parametersJson.value = currentText + (currentText ? `\n  "field": ${variable}` : `{\n  "field": ${variable}\n}`)
  ElMessage.info(`已添加变量模板，请调整参数名`)
}

// 格式化JSON
const formatParametersJson = () => {
  if (!parametersJson.value || parametersJson.value.trim() === '') {
    parametersJson.value = '{}'
    return
  }
  try {
    const parsed = JSON.parse(parametersJson.value)
    parametersJson.value = JSON.stringify(parsed, null, 2)
    ElMessage.success('JSON格式化成功')
  } catch (e) {
    ElMessage.warning('JSON格式错误，请检查')
  }
}


// 清空参数
const clearParameters = () => {
  parametersJson.value = '{}'
  localConfig.parameters = {}
  ElMessage.info('参数已清空')
}

// 更新参数
const updateParameters = () => {
  try {
    if (parametersJson.value && parametersJson.value.trim()) {
      // 先解析变量占位符（保留 {{xxx}} 格式）
      let jsonStr = parametersJson.value
      // 暂时不替换变量，保持原样存储
      localConfig.parameters = JSON.parse(jsonStr)
    } else {
      localConfig.parameters = {}
    }
  } catch (e) {
    console.warn('参数JSON格式错误:', e.message)
    // 不弹出错误，让用户继续编辑
  }
}

const updateName = () => {
  emit('update', { name: nodeName.value })
}

const saveConfig = async () => {
  if (!configFormRef.value) return
  // 执行表单校验
  await configFormRef.value.validate((valid) => {
    if (!valid) return

    const configToSave = {}

    if (props.node.type === 'LLM') {
      configToSave.modelConfigId = localConfig.modelConfigId
      configToSave.systemPrompt = localConfig.systemPrompt
      configToSave.userPrompt = localConfig.userPrompt
      configToSave.temperature = localConfig.temperature
      configToSave.outputVar = localConfig.outputVar
      configToSave.outputType = localConfig.outputType
      configToSave.arrayItemType = localConfig.arrayItemType
      configToSave.outputFields = localConfig.outputFields
      configToSave.autoFormatPrompt = localConfig.autoFormatPrompt
    } else if (props.node.type === 'RAG') {
      configToSave.query = localConfig.query
      configToSave.kbName = localConfig.kbName
      configToSave.topK = localConfig.topK
      configToSave.threshold = localConfig.threshold
      configToSave.outputVar = localConfig.outputVar
    } else if (props.node.type === 'FUNCTION') {
      configToSave.functionName = localConfig.functionName
      configToSave.parameters = localConfig.parameters
      configToSave.outputVar = localConfig.outputVar
    } else if (props.node.type === 'AGENT') {
      configToSave.agentId = localConfig.agentId
      configToSave.query = localConfig.query
      configToSave.outputVar = localConfig.outputVar
    } else if (props.node.type === 'CONDITION') {
      configToSave.expression = localConfig.expression
      configToSave.outputVar = localConfig.outputVar
    } else if (props.node.type === 'CODE') {
      configToSave.code = localConfig.code
      configToSave.language = localConfig.language
      configToSave.outputVar = localConfig.outputVar
    } else if (props.node.type === 'START') {
      configToSave.inputVariables = localConfig.inputVariables
    } else if (props.node.type === 'END') {
      configToSave.outputVariables = localConfig.outputVariables
    }

    emit('update', configToSave)
    emit('close')
    ElMessage.success('配置保存成功')
  })
}

const addInputVar = () => {
  if (!localConfig.inputVariables) localConfig.inputVariables = []
  localConfig.inputVariables.push({ name: '', description: '', defaultValue: '' })
}

const removeInputVar = (index) => {
  localConfig.inputVariables.splice(index, 1)
}

const addOutputVar = () => {
  if (!localConfig.outputVariables) localConfig.outputVariables = []
  localConfig.outputVariables.push({ name: '', source: '', description: '' })
}

const removeOutputVar = (index) => {
  localConfig.outputVariables.splice(index, 1)
}

// 加载函数列表
const loadFunctionList = async () => {
  if (functionLoading.value) return
  functionLoading.value = true
  try {
    const res = await functionApi.getFunctionList()
    console.log('函数列表API响应:', res)
    if (res.code === 200) {
      functionList.value = res.data || []
      console.log('加载函数列表成功，共', functionList.value.length, '个函数')
      if (functionList.value.length === 0) {
        ElMessage.warning('暂无可用函数，请先在工具管理中添加')
      }
    } else {
      console.error('函数列表响应异常:', res)
      ElMessage.error(res.msg || '加载函数列表失败')
    }
  } catch (error) {
    console.error('加载函数列表失败:', error)
    ElMessage.error('加载函数列表失败: ' + (error.message || '未知错误'))
  } finally {
    functionLoading.value = false
  }
}

// 表单校验规则
const formRules = computed(() => {
  const rules = {}
  const nodeType = props.node.type

  // LLM 节点：输出变量名必填
  if (nodeType === 'LLM') {
    rules.outputVar = [
      { required: true, message: '请填写输出变量名', trigger: 'blur' },
      { pattern: /^[a-zA-Z0-9_]+$/, message: '变量名仅支持字母、数字、下划线', trigger: 'blur' }
    ]
  }

  // RAG 节点：查询内容、输出变量名必填
  if (nodeType === 'RAG') {
    rules.query = [{ required: true, message: '请输入查询内容', trigger: 'blur' }]
    rules.outputVar = [
      { required: true, message: '请填写输出变量名', trigger: 'blur' },
      { pattern: /^[a-zA-Z0-9_]+$/, message: '变量名仅支持字母、数字、下划线', trigger: 'blur' }
    ]
  }

  // 函数节点：函数、参数、输出变量必填
  if (nodeType === 'FUNCTION') {
    rules.functionName = [{ required: true, message: '请选择函数', trigger: 'change' }]
    rules.outputVar = [
      { required: true, message: '请填写输出变量名', trigger: 'blur' },
      { pattern: /^[a-zA-Z0-9_]+$/, message: '变量名仅支持字母、数字、下划线', trigger: 'blur' }
    ]
  }

  // 条件节点：表达式必填
  if (nodeType === 'CONDITION') {
    rules.expression = [{ required: true, message: '请填写条件表达式', trigger: 'blur' }]
  }

  // 代码节点：代码、输出变量必填
  if (nodeType === 'CODE') {
    rules.code = [{ required: true, message: '请编写代码', trigger: 'blur' }]
    rules.outputVar = [
      { required: true, message: '请填写输出变量名', trigger: 'blur' },
      { pattern: /^[a-zA-Z0-9_]+$/, message: '变量名仅支持字母、数字、下划线', trigger: 'blur' }
    ]
  }

  // Agent 节点：Agent、查询内容必填
  if (nodeType === 'AGENT') {
    rules.agentId = [{ required: true, message: '请选择Agent', trigger: 'change' }]
    rules.query = [{ required: true, message: '请输入查询内容', trigger: 'blur' }]
    rules.outputVar = [
      { required: true, message: '请填写输出变量名', trigger: 'blur' },
      { pattern: /^[a-zA-Z0-9_]+$/, message: '变量名仅支持字母、数字、下划线', trigger: 'blur' }
    ]
  }

  return rules
})

const loadAgentList = async () => {
  agentLoading.value = true
  try {
    const res = await agentApi.list()
    if (res.code === 200) agentList.value = res.data || []
  } catch (error) {
    console.error('加载Agent列表失败:', error)
  } finally {
    agentLoading.value = false
  }
}

if (props.node.type === 'LLM') loadModelConfigs()
if (props.node.type === 'FUNCTION') loadFunctionList()
if (props.node.type === 'AGENT') loadAgentList()

watch(() => props.node.config, (newVal) => {
  if (newVal) {
    // 🔥 修复：不暴力清空所有值，只合并配置
    Object.keys(localConfig).forEach(key => {
      if (newVal[key] !== undefined) {
        localConfig[key] = newVal[key]
      }
    })
    if (props.node.type === 'START' && !localConfig.inputVariables?.length) {
      localConfig.inputVariables = []
    }
    if (props.node.type === 'END' && !localConfig.outputVariables?.length) {
      localConfig.outputVariables = []
    }

    if (props.node.type === 'LLM') {
      if (localConfig.systemPrompt === undefined) localConfig.systemPrompt = ''
      if (localConfig.userPrompt === undefined) localConfig.userPrompt = ''
      if (localConfig.outputType === undefined) localConfig.outputType = 'string'
      if (localConfig.arrayItemType === undefined) localConfig.arrayItemType = 'string'
      if (localConfig.outputFields === undefined) localConfig.outputFields = []
      if (localConfig.autoFormatPrompt === undefined) localConfig.autoFormatPrompt = false

      // 🔥 修复：加载已保存的模型详情
      if (localConfig.modelConfigId) {
        const model = modelConfigList.value.find(m => m.id === localConfig.modelConfigId)
        if (model) selectedModelDetail.value = model
      }
    }

    if (props.node.type === 'RAG') {
      if (!localConfig.topK || localConfig.topK === 0) {
        localConfig.topK = 5
      }
      if (localConfig.threshold === undefined) {
        localConfig.threshold = 0.5
      }
    }
    if (props.node.type === 'FUNCTION') {
      loadFunctionList()
    }

    if (newVal.parameters) {
      try {
        const params = newVal.parameters
        if (typeof params === 'object') {
          parametersJson.value = JSON.stringify(params, null, 2)
        } else if (typeof params === 'string') {
          parametersJson.value = params
        } else {
          parametersJson.value = '{}'
        }
      } catch (e) {
        parametersJson.value = '{}'
      }
    } else {
      parametersJson.value = '{}'
    }
  }
}, { deep: true, immediate: true })

// 设置输出变量的来源
const setOutputVarSource = (outputVar, source) => {
  outputVar.source = source
  // 自动设置描述
  if (source.startsWith('var.')) {
    const varName = source.substring(4)
    const nodeVar = nodeOutputVars.value.find(v => v.name === varName)
    if (nodeVar && !outputVar.description) {
      outputVar.description = `引用 ${nodeVar.fromNode} 的输出`
    }
  }
}


</script>

<style scoped>
.node-config-panel {
  padding: 20px;
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #0a0e27;
  overflow-y: auto;
}

/* 滚动条样式 */
.node-config-panel::-webkit-scrollbar {
  width: 6px;
}

.node-config-panel::-webkit-scrollbar-track {
  background: #0f1228;
  border-radius: 3px;
}

.node-config-panel::-webkit-scrollbar-thumb {
  background: #2a2f4a;
  border-radius: 3px;
}

/* 面板底部按钮 */
.panel-actions {
  margin-top: auto;
  padding-top: 20px;
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  border-top: 1px solid #2a2f4a;
  margin-top: 20px;
}

.panel-actions .el-button--default {
  background: #2a2f4a !important;
  border: 1px solid #3a3f5a !important;
  color: #cbd5e6 !important;
}

.panel-actions .el-button--default:hover {
  background: #3a3f5a !important;
  border-color: #667eea !important;
  color: #ffffff !important;
}

.panel-actions .el-button--primary {
  background: linear-gradient(135deg, #667eea, #764ba2) !important;
  border: none !important;
}

/* ========== 表单样式 ========== */
:deep(.el-form-item__label) {
  color: #cbd5e6 !important;
  font-weight: 500;
  font-size: 13px;
}

:deep(.el-input__wrapper) {
  background: #0f1228 !important;
  border: 1px solid #2a2f4a !important;
  border-radius: 10px;
  box-shadow: none !important;
}

:deep(.el-input__wrapper:hover) {
  border-color: #667eea !important;
}

:deep(.el-input__wrapper.is-focus) {
  border-color: #667eea !important;
  box-shadow: 0 0 0 2px rgba(102, 126, 234, 0.2) !important;
}

:deep(.el-input__inner) {
  color: #ffffff !important;
}

:deep(.el-input__inner::placeholder) {
  color: #64748b !important;
}

:deep(.el-textarea__inner) {
  background: #0f1228 !important;
  border: 1px solid #2a2f4a !important;
  border-radius: 10px;
  color: #ffffff !important;
}

:deep(.el-textarea__inner:focus) {
  border-color: #667eea !important;
  box-shadow: 0 0 0 2px rgba(102, 126, 234, 0.2) !important;
}

:deep(.el-textarea__inner::placeholder) {
  color: #64748b !important;
}

/* 选择器样式 */
:deep(.el-select .el-input__wrapper) {
  background: #0f1228 !important;
}

:deep(.el-select-dropdown) {
  background: #1a1f3a !important;
  border: 1px solid #2a2f4a !important;
  border-radius: 12px;
}

:deep(.el-select-dropdown__item) {
  color: #cbd5e6 !important;
}

:deep(.el-select-dropdown__item:hover) {
  background: #2a2f4a !important;
  color: #ffffff !important;
}

:deep(.el-select-dropdown__item.selected) {
  background: rgba(102, 126, 234, 0.15) !important;
  color: #a78bfa !important;
}

/* 分割线 */
:deep(.el-divider) {
  background-color: #2a2f4a !important;
  margin: 20px 0;
}

:deep(.el-divider__text) {
  background-color: #0a0e27;
  color: #a78bfa;
  font-weight: 500;
}

/* 描述列表 */
:deep(.el-descriptions) {
  --el-descriptions-table-bg: transparent !important;
}

:deep(.el-descriptions__label) {
  background: #0f1228 !important;
  color: #94a3b8 !important;
  border-color: #2a2f4a !important;
}

:deep(.el-descriptions__content) {
  background: #1a1f3a !important;
  color: #cbd5e6 !important;
  border-color: #2a2f4a !important;
}

/* 滑块样式 */
:deep(.el-slider__runway) {
  background-color: #2a2f4a;
}

:deep(.el-slider__bar) {
  background: linear-gradient(135deg, #667eea, #764ba2);
}

:deep(.el-slider__button) {
  border-color: #667eea;
}

/* 标签样式 */
:deep(.el-tag) {
  background: rgba(102, 126, 234, 0.15) !important;
  border: 1px solid rgba(102, 126, 234, 0.3) !important;
  color: #a78bfa !important;
}

:deep(.el-tag--success) {
  background: rgba(16, 185, 129, 0.15) !important;
  border-color: rgba(16, 185, 129, 0.3) !important;
  color: #34d399 !important;
}

:deep(.el-tag--warning) {
  background: rgba(245, 158, 11, 0.15) !important;
  border-color: rgba(245, 158, 11, 0.3) !important;
  color: #fbbf24 !important;
}

:deep(.el-tag--danger) {
  background: rgba(239, 68, 68, 0.15) !important;
  border-color: rgba(239, 68, 68, 0.3) !important;
  color: #f87171 !important;
}

:deep(.el-tag--info) {
  background: rgba(100, 116, 139, 0.15) !important;
  border-color: rgba(100, 116, 139, 0.3) !important;
  color: #94a3b8 !important;
}

/* 按钮样式 */
:deep(.el-button--primary.is-plain) {
  background: rgba(102, 126, 234, 0.15) !important;
  border: 1px solid rgba(102, 126, 234, 0.3) !important;
  color: #a78bfa !important;
}

:deep(.el-button--primary.is-plain:hover) {
  background: rgba(102, 126, 234, 0.25) !important;
  border-color: #667eea !important;
  color: #c4b5fd !important;
}

:deep(.el-button--default) {
  background: #1a1f3a !important;
  border: 1px solid #2a2f4a !important;
  color: #cbd5e6 !important;
}

:deep(.el-button--default:hover) {
  background: #2a2f4a !important;
  border-color: #667eea !important;
  color: #ffffff !important;
  transform: translateY(-1px);
}

:deep(.el-button--danger) {
  background: linear-gradient(135deg, #ef4444, #dc2626) !important;
  border: none !important;
}

/* 提示文字 */
.form-tip {
  font-size: 12px;
  color: #64748b;
  margin-top: 4px;
}

.var-code {
  background: #0f1228;
  padding: 2px 6px;
  border-radius: 6px;
  font-family: 'SF Mono', Monaco, 'Fira Code', monospace;
  font-size: 12px;
  color: #a78bfa;
  border: 1px solid #2a2f4a;
}

/* 输入变量编辑器 */
.input-vars-editor,
.output-vars-editor,
.output-fields-editor {
  width: 100%;
}

.input-var-row,
.output-var-row,
.output-field-row {
  display: flex;
  align-items: center;
  margin-bottom: 8px;
  gap: 8px;
  flex-wrap: wrap;
  background: #0f1228;
  padding: 8px;
  border-radius: 8px;
  border: 1px solid #2a2f4a;
}

.slider-value {
  margin-top: 8px;
  font-size: 12px;
  color: #a78bfa;
  font-weight: 500;
}

/* 提示词编辑器 */
.prompt-editor {
  width: 100%;
}

.prompt-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  flex-wrap: wrap;
  gap: 8px;
}

.variable-toolbar {
  display: flex;
  gap: 8px;
}

/* 快速变量插入 */
.quick-vars {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.quick-label {
  font-size: 12px;
  color: #64748b;
  min-width: 70px;
}

.var-tag {
  cursor: pointer;
  transition: all 0.2s ease;
}

.var-tag:hover {
  transform: translateY(-1px);
  opacity: 0.9;
}

/* 测试结果区域 */
.test-result {
  margin-top: 12px;
}

.test-result-content {
  margin-top: 12px;
  max-height: 300px;
  overflow-y: auto;
}

.result-documents {
  border: 1px solid #2a2f4a;
  border-radius: 10px;
  overflow: hidden;
}

.result-document {
  padding: 12px;
  border-bottom: 1px solid #2a2f4a;
}

.result-document:last-child {
  border-bottom: none;
}

.doc-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.doc-index {
  font-weight: 600;
  color: #a78bfa;
  font-size: 13px;
}

.doc-content {
  font-size: 12px;
  color: #cbd5e6;
  line-height: 1.5;
}

.test-error {
  margin-top: 12px;
  padding: 12px;
  background: rgba(239, 68, 68, 0.1);
  border-radius: 8px;
  color: #f87171;
  border: 1px solid rgba(239, 68, 68, 0.3);
}

/* 格式预览区域 */
.format-preview {
  background: #0f1228;
  border: 1px solid #2a2f4a;
  border-radius: 10px;
  padding: 12px;
  margin-top: 8px;
}

.format-preview-title {
  font-size: 12px;
  font-weight: 500;
  color: #a78bfa;
  margin-bottom: 8px;
}

.format-preview pre {
  background: #1a1f3a;
  padding: 10px;
  border-radius: 8px;
  margin: 8px 0;
  font-size: 11px;
  color: #cbd5e6;
  overflow-x: auto;
}

/* 字段选择器栏 */
.field-selector-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.field-selector-label {
  font-size: 12px;
  color: #64748b;
}

/* 输出预览区域 */
.output-preview {
  font-size: 12px;
  color: #cbd5e6;
}

.output-preview code {
  background: #0f1228;
  padding: 2px 6px;
  border-radius: 4px;
  color: #a78bfa;
  font-family: monospace;
}

.output-preview ul {
  margin: 8px 0;
  padding-left: 20px;
}

.output-preview li {
  margin: 4px 0;
}

/* ========== 提示词生成对话框样式 ========== */
.generate-result {
  max-height: 60vh;
  overflow-y: auto;
}

.generate-result::-webkit-scrollbar {
  width: 6px;
}

.generate-result::-webkit-scrollbar-track {
  background: #0f1228;
}

.generate-result::-webkit-scrollbar-thumb {
  background: #2a2f4a;
  border-radius: 3px;
}

.result-header {
  margin-bottom: 16px;
  text-align: center;
}

.result-section {
  margin-top: 20px;
}

.section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.section-title h4 {
  margin: 0;
  color: #ffffff;
  font-size: 14px;
  font-weight: 600;
}

.prompt-content {
  background: #0f1228;
  border: 1px solid #2a2f4a;
  border-radius: 10px;
  padding: 16px;
  max-height: 300px;
  overflow-y: auto;
}

.prompt-content pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: 'SF Mono', Monaco, 'Fira Code', monospace;
  font-size: 12px;
  line-height: 1.5;
  color: #cbd5e6;
}

.suggestions-list {
  margin: 0;
  padding-left: 20px;
  list-style: none;
}

.suggestions-list li {
  margin: 8px 0;
  color: #94a3b8;
  display: flex;
  align-items: center;
  gap: 8px;
}

.suggestions-list li .el-icon {
  color: #34d399;
}

/* ========== 对话框样式 ========== */
:deep(.el-dialog) {
  background: #1a1f3a !important;
  border: 1px solid #2a2f4a !important;
  border-radius: 20px !important;
}

:deep(.el-dialog__header) {
  border-bottom: 1px solid #2a2f4a;
  padding: 16px 20px;
}

:deep(.el-dialog__title) {
  color: #ffffff !important;
  font-weight: 600;
}

:deep(.el-dialog__body) {
  padding: 20px;
}

:deep(.el-dialog__footer) {
  border-top: 1px solid #2a2f4a;
  padding: 16px 20px;
}

:deep(.el-alert) {
  background: rgba(102, 126, 234, 0.1) !important;
  border: 1px solid rgba(102, 126, 234, 0.2) !important;
}

:deep(.el-alert__title) {
  color: #cbd5e6 !important;
}

:deep(.el-radio__label) {
  color: #cbd5e6 !important;
}

:deep(.el-radio.is-checked .el-radio__label) {
  color: #a78bfa !important;
}

:deep(.el-radio__inner) {
  background: #0f1228 !important;
  border-color: #2a2f4a !important;
}

:deep(.el-radio.is-checked .el-radio__inner) {
  background: #667eea !important;
  border-color: #667eea !important;
}

/* ========== 响应式 ========== */
@media screen and (max-width: 768px) {
  .node-config-panel {
    padding: 16px;
  }

  .input-var-row,
  .output-var-row,
  .output-field-row {
    flex-direction: column;
    align-items: stretch;
  }

  .prompt-toolbar {
    flex-direction: column;
    align-items: stretch;
  }

  .variable-toolbar {
    justify-content: flex-start;
  }
}

/* 变量选择器触发器样式 */
.variable-selector-trigger {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 5px 12px;
  background: #0f1228;
  border: 1px solid #2a2f4a;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.2s;
  min-height: 32px;
}

.variable-selector-trigger:hover {
  border-color: #667eea;
}

.variable-selector-trigger .placeholder {
  color: #64748b;
  font-size: 13px;
}

.variable-selector-trigger .arrow-icon {
  color: #94a3b8;
  transition: transform 0.2s;
}

.variable-selector-trigger:hover .arrow-icon {
  color: #a78bfa;
}

.empty-vars-tip {
  padding: 8px 0;
  text-align: center;
}
</style>