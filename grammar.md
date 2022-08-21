# Language Model Grammar

An *intent* or *command* is a user command to the system.

Each command can be given in different forms.

## Notation

### Optional

```
(Please) play the music
```

### Choice

```
[make, brew] coffee
```

### Slot

```
$slot_type:slot_variable
```

### Macro

```
@brew me a coffee
```

## Macros

- release = [release, BODYATTACK]
- track = [track, song]

## Commands

### Play Release

- Play (@release) `release number`

### Play Track

- Play ((@release]) `release number`) track `track number`

### Next Track

- (Play) next @track

### Previous Track

- (Play) previous @track

### Restart Track

- Restart
- Start over
- From the top

### Play Music

- Play (music)

### Stop Music

- Stop (music)

### Volume up

- Volume up

### Volume down

- Volume down
