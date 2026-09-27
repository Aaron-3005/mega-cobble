{
  name: 'Mega Sol',
  installCompatibility: function () {
  for (const pokemon of this.getAllPokemon()) {
    if (pokemon.m.megacobbleAbilities) continue;
    pokemon.m.megacobbleAbilities = true;
    const grounded = pokemon.isGrounded;
    pokemon.isGrounded = function (negateImmunity) {
      const result = grounded.call(this, negateImmunity);
      if (!result || !this.hasAbility('eelevate') || this.battle.suppressingAbility(this)) return result;
      if (this.battle.field.pseudoWeather.gravity || this.volatiles.smackdown ||
          (this.volatiles.ingrain && this.battle.gen >= 4) || this.hasItem('ironball')) return result;
      return false;
    };
    const weather = pokemon.effectiveWeather;
    pokemon.effectiveWeather = function () {
      const effect = this.battle.effect;
      if (this.battle.activePokemon && this.battle.activePokemon.hasAbility('megasol') &&
          effect && (effect.id === 'megasol' || effect.effectType === 'Move' || effect.effectType === 'Weather') &&
          effect.id !== 'electroshot') return 'sunnyday';
      return weather.apply(this, arguments);
    };
  }
  if (this.megacobbleAbilities) return;
  this.megacobbleAbilities = true;

  const singleEvent = this.singleEvent;
  this.singleEvent = function (event, effect, state, target) {
    const result = singleEvent.apply(this, arguments);
    if (result === false && event === 'Start' && effect.id === 'smackdown' &&
        target.hasAbility('eelevate') && !target.hasItem('ironball') &&
        !target.volatiles.ingrain && !this.field.pseudoWeather.gravity) {
      this.add('-start', target, 'Smack Down');
      return true;
    }
    return result;
  };

  const findHandlers = this.findEventHandlers;
  this.findEventHandlers = function (target, event, source) {
    let handlers = findHandlers.apply(this, arguments);
    if (event === 'WeatherModifyDamage' && target && target.hasAbility && target.hasAbility('megasol')) {
      handlers = handlers.filter(handler => handler.effect.effectType !== 'Weather');
    }
    if (source && source.hasAbility('megasol')) {
      if (event === 'ModifySpD') handlers = handlers.filter(handler => handler.effect.id !== 'sandstorm');
      if (event === 'ModifyDef') handlers = handlers.filter(handler => handler.effect.id !== 'snow');
      if (event === 'ModifyAccuracy') handlers = handlers.filter(handler =>
        handler.effect.id !== 'sandveil' && handler.effect.id !== 'snowcloak');
    }
    if (event !== 'TryHit') return handlers;
    const shields = ['protect', 'detect', 'kingsshield', 'spikyshield', 'banefulbunker',
      'obstruct', 'silktrap', 'burningbulwark', 'quickguard', 'wideguard', 'matblock', 'craftyshield'];
    return handlers.map(handler => {
      if (!shields.includes(handler.effect.id) || typeof handler.callback !== 'function') return handler;
      const callback = handler.callback;
      return Object.assign({}, handler, { callback(target, source, move) {
        const result = callback.apply(this, arguments);
        if (result === this.NOT_FAIL && source.hasAbility('piercingdrill') && move.flags.contact &&
            move.flags.protect && !move.isZOrMaxPowered) {
          target.getMoveHitData(move).megacobblePierced = true;
          return;
        }
        return result;
      }});
    });
  };

  const modifyDamage = this.actions.modifyDamage;
  this.actions.modifyDamage = function (baseDamage, source, target, move) {
    const damage = modifyDamage.apply(this, arguments);
    if (!target.getMoveHitData(move).megacobblePierced) return damage;
    this.battle.add('-ability', source, 'Piercing Drill');
    this.battle.add('-zbroken', target);
    return Math.max(1, this.battle.modify(damage, 0.25));
  };
},
  onSwitchInPriority: 100,
  onSwitchIn() { this.effect.installCompatibility.call(this); },
  onStart() { this.effect.installCompatibility.call(this); },
  onWeatherModifyDamagePriority: 1,
  onWeatherModifyDamage(damage, attacker, defender, move) {
    return this.dex.conditions.get('sunnyday').onWeatherModifyDamage.call(this, damage, attacker, defender, move);
  }
}
