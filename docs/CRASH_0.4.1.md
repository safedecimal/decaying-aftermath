# Краш на загрузке реестров — 0.4.1

Исходная сборка: 0.4.0; новая сборка: 0.4.1.

## Симптом

Fabulously Optimized с установленной 0.4.0 падал до появления титульного экрана:

```
Description: Registry Loading
java.lang.IllegalStateException: Failed to load registries due to errors
Errors: minecraft:recipe/decaying_aftermath:pemmican: Failed to parse decaying_aftermath:pemmican from pack decaying_aftermath
Caused by: java.lang.IllegalStateException: Unknown element name:food
```

Падение происходило в фазе загрузки реестра, поэтому список модов и их миксины
в стектрейсе к причине отношения не имеют.

## Причина

В `data/decaying_aftermath/recipe/pemmican.json` стояло
`"category": "food"` при типе `minecraft:crafting_shapeless`.

`category` в 26.3 десериализуется в два разных перечисления:

| тип рецепта | перечисление | допустимые значения |
| --- | --- | --- |
| `crafting_shaped`, `crafting_shapeless`, `crafting_transmute` | `CraftingBookCategory` | `building`, `redstone`, `equipment`, `misc` |
| `smelting`, `blasting`, `smoking`, `campfire_cooking` | `CookingBookCategory` | `food`, `blocks`, `misc` |

`food` существует только у плавки. Неизвестное значение роняет разбор рецепта,
а неразобранный элемент оставляет несвязанное значение в реестре
`minecraft:recipe`, из-за чего `MappedRegistry.freeze` бросает
`Unbound values in registry` и клиент завершается. Набор модов здесь ни при чём:
та же ошибка валит и чистую установку.

## Исправление

1. `pemmican.json`: `category` понижен до `misc`. Состав, количество и
   advancement не менялись.
2. Проверены остальные десять рецептов: `water_bottle.json` и
   `water_bottle_campfire.json` используют `food` при `smelting` и
   `campfire_cooking` — это корректно и оставлено как есть.
3. Добавлен `RecipeCategoryResourceTest`: каждый рецепт в datapack сверяется
   с перечислением, соответствующим его типу. Тест падает на возвращённом
   `food`, то есть ловит именно эту регрессию.

## Проверено

- `./gradlew build` — успешно, 251 тест без падений.
- В `build/libs/decaying-aftermath-0.4.1.jar` лежит исправленный рецепт.
- Игра не запускалась; исправление подтверждено разбором рецепта, сборкой и
  тестом, а не загрузкой мира.
