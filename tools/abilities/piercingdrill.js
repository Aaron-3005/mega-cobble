{
  name: 'Piercing Drill',
  installCompatibility: __INSTALL_COMPAT__,
  onSwitchInPriority: 100,
  onSwitchIn() { this.effect.installCompatibility.call(this); },
  onStart() { this.effect.installCompatibility.call(this); }
}
