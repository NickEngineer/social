<template>
  <v-app v-if="navigations && navigations.length" class="spaceMenuParent white">
    <div class="space-action-menu">
      <div v-for="action in spaceMenuActionComponents" v-if="action.enabled" :key="action.key"
           :class="`${action.appClass} ${action.typeClass}`">
        <div v-if="action.component" :ref="action.key">
          <component v-dynamic-events="action.component.events"
                     v-bind="action.component.props ? action.component.props : {}"
                     :is="action.component.name"></component>
        </div>
        <div v-else-if="action.element" :ref="action.key" v-html="action.element.outerHTML">
        </div>
        <div v-else-if="action.html" :ref="action.key" v-html="action.html">
        </div>
      </div>
    </div>
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
import {spaceMenuActionComponents} from '../extension.js';

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
  data() {
    return {
      spaceMenuActionComponents: spaceMenuActionComponents
    };
  },
  computed: {
    isMobile() {
      return this.$vuetify.breakpoint.sm || this.$vuetify.breakpoint.xs;
    },
  },
  created() {
    document.addEventListener('refreshSpaceNavigations', () => {
      this.$spaceService.getSpaceNavigations(eXo.env.portal.spaceId)
        .then(data => {
          // Compute URI of nodes of old navigation
          if (data && data.length) {
            data.forEach(nav => {
              const oldNav = this.navigations.find(oldNav => oldNav.id === nav.id);
              if (oldNav) {
                nav.uri = oldNav.uri;
              } else if (nav.uri && nav.uri.indexOf('/') >= 0) {
                nav.uri = nav.uri.split('/')[1];
              }
            });
            this.navigations = data;
          }
          return this.$nextTick();
        })
        .then(() => this.$root.$emit('application-loaded'));
    });
  },
  mounted() {
    this.$root.$emit('application-loaded');
  },
};
</script>