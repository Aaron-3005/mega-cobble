{
  name: 'Mega Sol',
  installCompatibility: __INSTALL_COMPAT__,
  onSwitchInPriority: 100,
  onSwitchIn() { this.effect.installCompatibility.call(this); },
  onStart() { this.effect.installCompatibility.call(this); },
  onWeatherModifyDamagePriority: 1,
  onWeatherModifyDamage(damage, attacker, defender, move) {
    return this.dex.conditions.get('sunnyday').onWeatherModifyDamage.call(this, damage, attacker, defender, move);
  }
}
