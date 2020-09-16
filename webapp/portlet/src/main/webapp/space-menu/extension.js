function getExtensionsByType(type) {
  return extensionRegistry.loadExtensions('space-menu', type);
}

export function getCallComponents() {
  if (extensionRegistry) {
    return getExtensionsByType('call-component');
  } else {
    return false;
  }
}


