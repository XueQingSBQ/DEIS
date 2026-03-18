<template>
  <Teleport to="body">
    <div class="toast-viewport" aria-live="polite" aria-atomic="true">
      <TransitionGroup name="toast-list" tag="div" class="toast-stack">
        <article v-for="item in state.items" :key="item.id" class="toast-card" :class="`toast-${item.tone}`">
          <div class="toast-head">
            <strong>{{ item.title }}</strong>
            <button class="toast-close" type="button" @click="dismissToast(item.id)">关闭</button>
          </div>
          <p class="toast-message">{{ item.message }}</p>
        </article>
      </TransitionGroup>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { dismissToast, useToastState } from "../feedback/toast";

const state = useToastState();
</script>
