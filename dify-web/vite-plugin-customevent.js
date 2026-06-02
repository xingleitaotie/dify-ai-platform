// vite-plugin-customevent.js
export default function customEventPlugin() {
    return {
        name: 'custom-event-polyfill',
        buildStart() {
            // 在 Node 环境中添加 CustomEvent
            if (typeof globalThis.CustomEvent === 'undefined') {
                globalThis.CustomEvent = class CustomEvent extends Event {
                    constructor(type, options = {}) {
                        super(type, options);
                        this.detail = options.detail || null;
                    }
                };
                console.log('[polyfill] CustomEvent added to globalThis');
            }
        },
        // 同时也为浏览器环境提供 polyfill
        transformIndexHtml(html) {
            return html.replace('</head>', `
        <script>
          if (typeof window.CustomEvent === 'undefined') {
            window.CustomEvent = class CustomEvent extends Event {
              constructor(type, options = {}) {
                super(type, options);
                this.detail = options.detail || null;
              }
            };
          }
        </script>
      </head>`);
        }
    };
}