<template>
  <v-flex
    :id="id"
    class="datePickerComponent">
    <v-menu
      ref="selectDateMenu"
      v-model="menu"
      :content-class="menuId"
      :close-on-content-click="false"
      :disabled="disabled"
      :top="top"
      :left="left"
      class="datePickerMenu"
      transition="scale-transition"
      attach
      allow-overflow
      offset-y
      eager>
      <input
        v-slot:activator="{ on }"
        slot="activator"
        slot-scope="{ on }"
        v-model="dateFormatted"
        :disabled="disabled"
        :placeholder="placeholder"
        :required="required"
        class="ignore-vuetify-classes datePickerText flex-grow-0"
        readonly
        type="text"
        v-on="on" />
      <v-date-picker
        v-model="date"
        :first-day-of-week="1"
        :type="periodType"
        :locale="lang"
        :min="minDate"
        class="border-box-sizing"
        @input="menu = false" />
    </v-menu>
  </v-flex>
</template>

<script>

export default {
  props: {
    placeholder: {
      type: String,
      default: function() {
        return null;
      },
    },
    prependIcon: {
      type: String,
      default: function() {
        return 'uiIconDatePicker';
      },
    },
    periodType: {
      type: String,
      default: function() {
        return 'date';
      },
    },
    minValue: {
      type: String,
      default: function() {
        return null;
      },
    },
    value: {
      type: String,
      default: function() {
        return null;
      },
    },
    minValueErrorMessage: {
      type: String,
      default: function() {
        return null;
      },
    },
    lang: {
      type: String,
      default: function() {
        return eXo.env.portal.language;
      },
    },
    disabled: {
      type: Boolean,
      default: function() {
        return false;
      },
    },
    required: {
      type: Boolean,
      default: function() {
        return false;
      },
    },
    returnIso: {
      type: Boolean,
      default: function() {
        return false;
      },
    },
    top: {
      type: Boolean,
      default: function() {
        return false;
      },
    },
    left: {
      type: Boolean,
      default: function() {
        return false;
      },
    },
    format: {
      type: Object,
      default: function() {
        return {
          year: 'numeric',
          month: 'long',
          day: 'numeric'
        };
      },
    },
  },
  data: () => ({
    id: `DatePicker${parseInt(Math.random() * 10000)
      .toString()
      .toString()}`,
    menuId: `DatePickerMenu${parseInt(Math.random() * 10000)
      .toString()
      .toString()}`,
    date: null,
    dateFormatted: null,
    dateValue: null,
    menu: false,
  }),
  computed: {
    minDate() {
      if (this.minValue) {
        const dateObj = this.$dateUtil.getDateObjectFromString(this.minValue, true);
        return this.$dateUtil.getISODate(dateObj);
      } else {
        return null;
      }
    }
  },
  watch: {
    value(newVal, oldVal) {
      if (oldVal !== newVal) {
        this.computeDate();
      }
    },
    disabled() {
      this.emitDateValue();
    },
    date() {
      this.emitDateValue();
    },
  },
  mounted() {
    // Force to close other DatePicker menus when opening a new one 
    $('.datePickerComponent input').on('click', (e) => {
      if (e.target && !$(e.target).parents(`#${this.id}`).length) {
        this.menu = false;
      }
    });

    // Force to close DatePickers when clicking outside
    $(document).on('click', (e) => {
      if (e.target && !$(e.target).parents(`.${this.menuId}`).length) {
        this.menu = false;
      }
    });

    this.computeDate();
  },
  methods: {
    emitDateValue() {
      const dateObj = this.date && new Date(this.date) || null;
      if (this.disabled) {
        this.dateValue = null;
      } else {
        if (this.returnIso) {
          this.dateValue = this.date;
        } else {
          this.dateValue = dateObj && dateObj.getTime() || null;
        }
      }
      if (this.dateValue) {
        this.dateFormatted = this.$dateUtil.formatDateObjectToDisplay(dateObj, this.format, this.lang);
      } else {
        this.dateFormatted = null;
      }
      this.$emit('input', this.dateValue);
    },
    computeDate() {
      if (this.value && String(this.value).trim()) {
        const dateObj = this.$dateUtil.getDateObjectFromString(String(this.value).trim(), true);
        this.date = this.$dateUtil.getISODate(dateObj);
      } else {
        this.date = this.$dateUtil.getISODate(new Date());
      }
    },
  },
};
</script>
