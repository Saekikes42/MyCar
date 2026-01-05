# Локальный API

Базовый адрес на компьютере: `http://127.0.0.1:5114`. JSON использует camelCase, ошибки возвращают HTTP 4xx/5xx и `{"success": false, "message": "..."}`. Тела POST/PUT должны быть JSON-объектами с `Content-Type: application/json` и размером до 64 KiB.

| Метод | Маршрут | Содержимое / результат |
| --- | --- | --- |
| GET | `/health` | Состояние и версия базы |
| GET | `/api/car` | Массив автомобилей с годами, цветами и комплектациями |
| GET | `/api/car/{id}` | Один автомобиль |
| POST | `/api/Auth/Register` | `name`, `email`, `birthDate`, `password`; профиль |
| POST | `/api/Auth/Login` | `email`, `password`; профиль и `token` |
| POST | `/api/Auth/Logout` | `{}`; удаление текущей серверной сессии |
| GET | `/api/Auth/GetProfile/{userId}` | Профиль текущего пользователя |
| PUT | `/api/Auth/UpdateProfile/{userId}` | `name`, `email`, `birthDate`, `password`; обновлённый профиль |
| GET | `/api/Cart/GetCart/{userId}` | `items`, `totalQuantity`, `totalPrice` |
| POST | `/api/Cart/AddToCart` | `userId`, `cartItem: {id, quantity}`; добавление количества |
| PUT | `/api/Cart/UpdateQuantity` | То же тело; установка количества, `0` удаляет позицию |
| POST | `/api/Order/CreateOrder` | `userId`, `itemId`, `deliveryMethod`, `salonLocation`, `paymentMethod`, `notes` |
| GET | `/api/Order/GetOrders/{userId}` | Массив заявок от новых к старым |
| GET | `/images/{name}` | Одно из трёх разрешённых изображений каталога |

Все операции с профилем, корзиной и заказами требуют `Authorization: Bearer <token>`. Сервер проверяет соответствие `userId` владельцу сессии. Каталог открыт без авторизации.

Регистрация не выполняет вход автоматически. Токен выдаётся при входе и действует семь дней. Пароль в запросе обновления профиля может быть пустым: это сохраняет текущий пароль. Непустой пароль проверяется и заменяется; после этого все сессии пользователя удаляются и нужно войти заново.

Дата рождения принимается как `ГГГГ-ММ-ДД` или `ДД.ММ.ГГГГ`, будущая дата запрещена. Пароль: 8–64 символа, заглавная и строчная буквы, цифра и спецсимвол. Email уникален без учёта регистра.

`deliveryMethod`: `Салон` или `Доставка`; `paymentMethod`: `Кредит` или `Наличные`. Эти значения описывают заявку. Новый заказ получает статус `Pending`. Администрирование статусов и отмена не входят в текущий API.
