<template>
  <div class="webConferencingCallContainer webConferencingSpaceContainer pull-left">
    <div v-for="callComponent in callComponents" v-if="callComponent.enabled" :key="callComponent.key"
         :class="`${callComponent.appClass}Action`">
      <component v-dynamic-events="callComponent.component.events"
                 v-bind="callComponent.component.props ? callComponent.component.props : {}"
                 :is="callComponent.component.name" :ref="callComponent.key"></component>
    </div>
  </div>
</template>

<script>
import {getCallComponents} from '../extension.js';

export default {
  name : 'CallNav',
  created() {
    this.callComponents = getCallComponents();
    console.log(`callComponents: ${this.callComponents}`);
  }
};
</script>

<style scoped>


</style>