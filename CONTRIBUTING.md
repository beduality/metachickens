# Contributing

## Useful Commands for Testing

```
/summon minecraft:chicken ~ ~ ~ {Age:-6000}

/data merge entity @e[type=chicken,limit=1,sort=nearest] {Age:0}

/execute as @e[type=chicken,distance=..10] run data merge entity @s {EggLayTime:1}
```
