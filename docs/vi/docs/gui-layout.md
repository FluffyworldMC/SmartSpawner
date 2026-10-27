# Bố Cục GUI

SmartSpawner cho phép tùy chỉnh hoàn toàn GUI spawner bằng các file trong `plugins/SmartSpawner/gui_layouts/`.

## Cấu Trúc Thư Mục

```
plugins/SmartSpawner/gui_layouts/
└── default/
    ├── main_gui.yml
    ├── storage_gui.yml
    ├── sell_confirm_gui.yml
    └── filter_gui.yml
```

`default` là bố cục duy nhất đi kèm. Bạn vẫn có thể tạo thư mục khác và chọn bằng `gui_layout`
trong `config.yml` hoặc `/ss gui_layout <name>`.

## Mọi chữ trong GUI nằm ngay tại slot

Không còn file ngôn ngữ GUI riêng. Mỗi nút giữ icon, tên, lore, hành động và phần chữ phụ trong
cùng một khối slot:

```yaml
slot_12:
  material: CHEST
  name: "&a&lSTORAGE"
  lore:
    - "&7Items: &f{current_items}&7/&f{max_slots}"
    - "&7Worth: &6${total_sell_price}"
    - "&eClick to open"
  loot_items: "  &8• &f{item_name}&f: &e{amount}"
  loot_items_empty: "  &8• &fNo items stored"
  click:
    action: "open_storage"
    sound: block.chest.open
```

Tiêu đề là khóa cấp đầu file (`title`, và `title_single` cho GUI kho). Mọi dòng đều nhận mã màu
`&`, `&#RRGGBB`, các placeholder ghi ở đầu mỗi file và PlaceholderAPI như `%player_name%`.

Các khóa chữ phụ:

| Khóa | Dùng cho | Ý nghĩa |
|---|---|---|
| `loot_items` | nút kho / thông tin / tóm tắt bán | định dạng từng dòng cho `{loot_items}` |
| `loot_items_empty` | như trên | dòng hiện khi không có vật phẩm |
| `status_full` | nút thông tin | chữ thay cho `{time}` khi kho đầy |
| `status_inactive` | nút thông tin | chữ thay cho `{time}` khi spawner tắt |
| `status_no_loot` | nút thông tin | chữ thay cho `{time}` khi chưa có loot |
| `sort_selected` / `sort_unselected` / `sort_empty` | nút sắp xếp | danh sách `{available_items}` |
| `name_with_exp` / `lore_with_exp` | nút xác nhận bán | dùng khi bán kèm nhận EXP |

## Cách Chỉnh Sửa Của Bạn Được Giữ Lại

Các nút trong file bố cục là của bạn. Dời một nút sang slot khác hoặc xóa nó đi, nó sẽ giữ nguyên như vậy qua mỗi lần khởi động lại và `/ss reload`. Không có nút nào bị đặt lại ở slot cũ.

Đổi lại, nút mới thêm trong bản cập nhật sau sẽ không xuất hiện trong file bạn đang có. Hãy xem changelog khi cập nhật và tự chép nút mới vào nếu bạn muốn dùng. Các tùy chọn không phải nút, ví dụ `skip_main_gui` và `open_sound`, vẫn được thêm tự động.

Muốn làm lại từ đầu, xóa file rồi khởi động lại server. File sẽ trở lại đúng như bản gốc, kèm cả phần chú thích.

## Cooldown Nút

Mọi nút hỗ trợ cooldown tùy chọn riêng cho từng người chơi:

```yaml
slot_12:
  material: CHEST
  enabled: true
  cooldown: "2s"
  click:
    action: "open_storage"
```

Giá trị dùng cùng định dạng thời gian với `config.yml`: `20`, `5s`, `10m`, `1h`, `1m_30s`, v.v.

- Nút đang cooldown không chạy action và gửi thông báo `action_not_ready` với placeholder `{time}`.
- Mọi nút action có debounce chống spam **100 ms** ngay cả khi không đặt `cooldown`.
- Cooldown cũng có thể được ghi đè trong nhánh điều kiện `if:`.

```yaml
slot_14:
  material: PLAYER_HEAD
  enabled: true
  if:
    sell_integration:
      cooldown: "3s"
      click:
        action: "sell_and_exp"
    no_sell_integration:
      click:
        action: "none"
```

## Âm Thanh Khi Nhấn

Nút điều hướng dùng một `sound` khi chuyển trang thành công:

```yaml
slot_1:
  material: ARROW
  enabled: true
  click:
    action: "previous_page"
    sound: ui.button.click
```

Nút action dùng âm thanh riêng khi thành công hoặc thất bại:

```yaml
slot_6:
  material: BUNDLE
  enabled: true
  click:
    action: "take_all"
    sound_success:
      name: entity.item.pickup
      volume: 1.0
      pitch: 1.0
    sound_fail: block.note_block.pling
```

Nhấp trái và phải có thể dùng action cùng âm thanh riêng:

```yaml
left_click:
  action: "sell_and_exp"
  sound: ui.button.click
  sound_success: block.note_block.bell
  sound_fail: block.note_block.pling
right_click:
  action: "collect_exp"
  sound: ui.button.click
```

Mỗi khóa âm thanh nhận một Bukkit key hoặc danh sách kèm `volume` và `pitch`:

```yaml
sound_success:
  - block.note_block.bell
  - name: entity.experience_orb.pickup
    volume: 0.8
    pitch: 1.2
```

Đặt khóa âm thanh thành `none` để tắt.

## Texture Player Head Tùy Chỉnh

Nút dùng `material: PLAYER_HEAD` có thể hiển thị skin texture qua `custom_texture`:

```yaml
slot_14:
  material: PLAYER_HEAD
  custom_texture: "df5de940bfe499c59ee8dac9f9c3919e7535eff3a9acb16f4842bf290f4c679f"
  enabled: true
  info_button: true
  if:
    sell_integration:
      left_click:
        action: "sell_and_exp"
      right_click:
        action: "none"
    no_sell_integration:
      click:
        action: "none"
```

- `custom_texture` là hash trong URL texture, không gồm tiền tố `http://textures.minecraft.net/texture/`.
- Có thể tìm texture tại [Minecraft-Heads.com](https://minecraft-heads.com/).
- Nếu bỏ qua trên nút thông tin, plugin dùng head mob từ `spawner_mobs.yml`.
- Texture được cache sau lần tải đầu, không ảnh hưởng hiệu năng khi mở GUI sau đó.

::: warning
`custom_texture` bị bỏ qua nếu `material` được ghi đè thành giá trị khác `PLAYER_HEAD` trong nhánh `if:`.
:::

## Ghi Đè Theo Điều Kiện

Dùng `if:` để đổi giao diện hoặc hành vi nút theo trạng thái máy chủ:

```yaml
slot_14:
  material: PLAYER_HEAD
  custom_texture: "default_texture_hash_here"
  enabled: true
  info_button: true
  if:
    sell_integration:
      material: EMERALD
      left_click:
        action: "sell_and_exp"
    no_sell_integration:
      click:
        action: "none"
```

Điều kiện hiện có gồm `sell_integration` và `no_sell_integration`.
