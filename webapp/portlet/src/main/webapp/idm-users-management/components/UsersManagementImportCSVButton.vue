<template>
  <v-btn
    :disabled="disabled"
    class="btn dropdown-button"
    @click="openFileSelection">
    <i class="uiIconImport mr-md-3" />
    {{ $t('UsersManagement.importCSV') }}
    <v-file-input
      v-if="!disabled"
      ref="usersCSVInput"
      class="importCSVUsersButton hidden mr-4"
      prepend-icon=""
      accept=".csv"
      clearable
      @change="importUsers">
    </v-file-input>
  </v-btn>
</template>

<script>
export default {
  data: () => ({
    disabled: false,
  }),
  created() {
    this.$root.$on('importCSVStarted', () => this.disabled = true);
    this.$root.$on('importCSVError', () => this.disabled = false);
    this.$root.$on('importCSVFinished', () => this.disabled = false);
  },
  methods: {
    openFileSelection() {
      this.$refs.usersCSVInput.$el.getElementsByTagName('input')[0].click();
    },
    importUsers(file) {
      if (file && file.size) {
        const uploadId = this.$uploadService.generateRandomId();
        this.$root.$emit('importCSVStarted', uploadId);
        return this.$uploadService.upload(file, uploadId)
          .then(() => this.$userService.importUsers(uploadId))
          .then(() => this.$root.$emit('importCSVProgress', uploadId))
          .catch(error => this.$root.$emit('importCSVError', error));
      }
    },
  },
};
</script>