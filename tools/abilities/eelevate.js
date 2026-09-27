{
  name: 'Eelevate',
  flags: { breakable: 1 },
  installCompatibility: __INSTALL_COMPAT__,
  onSwitchInPriority: 100,
  onSwitchIn() { this.effect.installCompatibility.call(this); },
  onStart() { this.effect.installCompatibility.call(this); },
  onSourceAfterFaint(length, target, source, effect) {
    if (effect && effect.effectType === 'Move') {
      this.boost({ [source.getBestStat(true, true)]: length }, source);
    }
  }
}
