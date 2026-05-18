Создать программу-игрушку в стиле «Тамагочи», демонстрирующую **принцип инкапсуляции**. Основная логика питомца спрятана внутри класса `Pet`, а графический интерфейс на Tkinter общается с ним только через разрешённые методы и свойства, не имея возможности напрямую подкрутить цифры.


Вы запускаете виртуального питомца по имени «Пушистик». Он может быть счастлив, голоден или утомлён. Ваши действия — покормить или поиграть — напрямую влияют на его самочувствие. Если голод достигает критической отметки или энергия падает до нуля, питомец погибает, и игра останавливается.

---

### Технические требования

#### 1. Класс `Pet` (питомец)
Реализуйте класс со следующими **приватными** атрибутами (через `__`):
- `__name` – имя (строка, задаётся один раз при создании),
- `__hunger` – уровень голода (целое число от 0 до 100, стартовое = 30, 100 – очень голоден),
- `__happiness` – уровень счастья (0..100, стартовое = 70),
- `__energy` – уровень энергии (0..100, стартовое = 50),
- `__is_alive` – жив ли питомец (булево значение, стартовое = True).

**Свойства только для чтения** (`@property`) для каждого из пяти атрибутов. Никаких сеттеров – изменение извне запрещено.

**Публичные методы-действия:**
- `feed(amount=20)` – уменьшает голод на `amount` (но не ниже 0) и немного восстанавливает энергию (+5, но не выше 100).
- `play(duration=15)` – увеличивает счастье на `duration`, уменьшает энергию на `duration//2` и повышает голод на 5 (все с ограничением 0..100).
- `status()` – возвращает строку-настроение на основе текущих показателей:
  * "Счастлив и сыт" при счастье > 70 и голоде < 30,
  * "Очень голоден" при голоде > 70,
  * "Ужасно устал" при энергии < 20,
  * "Питомец ушёл... RIP" если питомец не жив,
  * иначе "Всё в порядке".
- `levels_text()` – возвращает строку вида `Голод: 30  |  Счастье: 70  |  Энергия: 50`.

**Приватные вспомогательные методы:**
- `__clamp(value)` – возвращает значение, ограниченное диапазоном 0..100.
- `__validate_life()` – проверяет, не достиг ли голод 100 или энергия 0, и если да, устанавливает `__is_alive = False`.

**Важно:** оба метода `feed` и `play` должны вызывать `__validate_life()` в конце, а также не выполнять действий, если питомец уже мёртв (`if not self.__is_alive: return`).

Инкапсуляция должна быть непроницаемой: любой прямой доступ к `pet.__hunger` извне класса должен приводить к `AttributeError`. GUI работает только через чтение свойств и вызов публичных методов.

---

#### 2. Графический интерфейс `TamagotchiApp` (Tkinter)
Создайте класс-обёртку для окна, который:
- в конструкторе получает корневой объект `root`,
- создаёт экземпляр `Pet("Пушистик")` (или с любым именем),
- строит интерфейс, содержащий:
  * метку с именем питомца
  * метку с текущими уровнями – текст берётся из `levels_text()`,
  * метку со статусом  – текст из `status()`,
  * три кнопки: «Покормить», «Поиграть», «Выход».

**Логика обновления:**
- После каждого нажатия на кнопки «Покормить» или «Поиграть» вызывается соответствующий метод питомца, затем – отдельный метод `update_ui()`.
- `update_ui()` обновляет тексты всех меток и проверяет, жив ли питомец. Если `not self.pet.is_alive`:
  * кнопки «Покормить» и «Поиграть» делаются неактивными (`state=tk.DISABLED`),
  * цвет фона окна меняется на `lightgray`,
  * цвет текста статуса становится красным.

---

### Скелет программы (рекомендации)
```python
import tkinter as tk

class Pet:
    def __init__(self, name: str):
        # инициализация приватных атрибутов
        pass
    # свойства, feed, play, status, levels_text, __clamp, __validate_life

class TamagotchiApp:
    def __init__(self, root):
        self.root = root
        self.root.title("Тамагочи")
        self.pet = Pet("Пушистик")
        # создание и размещение меток и кнопок
        self.update_ui()

    def feed_pet(self):
        self.pet.feed()
        self.update_ui()

    def play_pet(self):
        self.pet.play()
        self.update_ui()

    def update_ui(self):
        # обновление текстов, блокировка кнопок при смерти
        pass

if __name__ == "__main__":
    root = tk.Tk()
    app = TamagotchiApp(root)
    root.mainloop()
```

class Pet:
    def __init__(self, name):
        self.__name = name
        self.__hunger = 30
        self.__happiness = 70
        self.__energy = 50
        self.__is_alive = True

    @property
    def name(self):
        return self.__name
    @property
    def hunger(self):
        return self.__hunger
    @property
    def happiness(self):
        return self.__happiness
    @property
    def energy(self):
        return self.__energy
    @property
    def is_alive(self):
        return self.__is_alive
    
    def feed(self, amount=20):
        if not self.is_alive:
            if amount>0:
                self.hunger = max(0, self.hunger-amount)
                self.energy = min(100, self.energy + 5)

    def play(self, duration=15):
        if not self.is_alive:
            if duration>0:
                self.happiness = min(100, self.happiness+duration)
                self.energy = max(0, self.energy+(duration//2))
                self.hunger = min(100, self.hunger+5)
                self.__validate_life(self)

    def status(self):
        HF = 'Счастлив и сыт'
        VH = 'Очень голоден'
        RT = 'Ужасно устал'
        PG = 'Питомец ушёл... RIP'
        AF = 'Всё в порядке'
        if self.happiness > 70 and self.hunger < 30:
            return HF
        elif self.hunger > 70:
            return VH
        elif self.energy < 20:
            return RT
        elif self.is_alive == False:
            return PG
        else:
            return AF
        
    def levels_text(self):
        return (f'Голод: {self.hunger}  |  Счастье: {self.happiness}  |  Энергия: {self.energy}')
    
    def __validate_life(self):
        if self.hunger > 99 or self.energy < 1:
            self.is_alive = False


