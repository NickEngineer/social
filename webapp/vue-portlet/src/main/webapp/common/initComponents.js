import ExoDrawer from './components/ExoDrawer.vue';
import ExoConfirmDialog from './components/ExoConfirmDialog.vue';
import ExoUserAvatarsList from './components/ExoUserAvatarsList.vue';
import ExoUserAvatar from './components/ExoUserAvatar.vue';
import ExoIdentitySuggester from './components/ExoIdentitySuggester.vue';

const components = {
  'exo-user-avatars-list': ExoUserAvatarsList,
  'exo-user-avatar': ExoUserAvatar,
  'exo-drawer': ExoDrawer,
  'exo-confirm-dialog': ExoConfirmDialog,
  'exo-identity-suggester': ExoIdentitySuggester,
};

for (const key in components) {
  Vue.component(key, components[key]);
}
