{
  name: 'Spicy Spray',
  onDamagingHit(damage, target, source, move) {
    source.trySetStatus('brn', target);
  }
}
