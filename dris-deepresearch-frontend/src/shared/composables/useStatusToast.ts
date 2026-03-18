import type { Ref } from "vue";
import { watch } from "vue";

import { showToast, type ToastTone } from "../feedback/toast";

function inferToastTone(message: string): ToastTone {
  const value = message.trim().toLowerCase();
  if (!value) {
    return "info";
  }
  if (/(失败|错误|异常|invalid|forbidden|unauthorized|denied|not found|failed|error|拒绝|不可|必需|required|超时)/i.test(value)) {
    return "error";
  }
  if (/(成功|已|完成|通过|可达|就绪|复制|创建|生成|绑定|加载|连接|登录|上传|下载)/i.test(value)) {
    return "success";
  }
  return "info";
}

export function useStatusToast(statusMessage: Ref<string>, title = "操作反馈"): void {
  watch(statusMessage, (value, oldValue) => {
    const message = (value || "").trim();
    const previous = (oldValue || "").trim();
    if (!message || message === previous) {
      return;
    }
    showToast({
      title,
      message,
      tone: inferToastTone(message)
    });
  });
}
