<template>
  <v-app v-if="navigations && navigations.length" class="spaceMenuParent white">
    <v-dialog
      v-if="isMobile"
      :value="true"
      hide-overlay
      persistent
      scrollable
      internal-activator
      content-class="spaceButtomNavigation white">
      <v-bottom-navigation
        :value="selectedNavigationUri"
        grow
        color="tertiary"
        background-color="transparent"
        class="spaceButtomNavigationParent"
        flat>
        <v-btn
          v-for="nav in navigations"
          :key="nav.id"
          :value="nav.uri"
          :href="nav.uri"
          class="subtitle-2 spaceButtomNavigationItem">
          <span>{{ nav.label }}</span>
          <i :class="nav.icon"></i>
        </v-btn>
      </v-bottom-navigation>
    </v-dialog>
    <v-tabs
      v-else
      :value="selectedNavigationUri"
      active-class="SelectedTab"
      class="mx-auto"
      show-arrows
      center-active
      slider-size="4">
      <v-tab
        v-for="nav in navigations"
        :key="nav.id"
        :value="nav.id"
        :href="nav.uri"
        class="spaceNavigationTab">
        {{ nav.label }}
      </v-tab>
    </v-tabs>
  </v-app>
</template>

<script>
export default {
  props: {
    navigations: {
      type: Array,
      default: () => [],
    },
    selectedNavigationUri: {
      type: String,
      default: null,
    },
  },
  computed: {
    isMobile() {
      return this.$vuetify.breakpoint.sm || this.$vuetify.breakpoint.xs;
    },
  },
  created() {
    document.dispatchEvent(new CustomEvent('hideTopBarLoading'));
  },
};
</script>