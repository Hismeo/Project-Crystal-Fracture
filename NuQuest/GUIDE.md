# 对话框使用教程
你可在[示例文件](example)找到能直接运行的例子

### 对话文件名称内容

这是一个对话文件  
xxx.json （名字任意，允许存放子文件）

```json5
{
  // 必须
  "dialog_id": "test_dialog",
  // 必须
  "dialog_texts": [
    {
      "title": "你好，冒险者！",
      // 可直接填原始值，会作为image读取
      "image_group": "minecraft:textures/gui/demo_background.png",
      "image_group": [
        {
          //必须
          "image": "minecraft:textures/gui/demo_background.png",
          "image_place_type": "FRONT",
          "image_config": {
            "x": "@screenwidth / 2 - 32",
            "y": "@screenheight / 2 - 32",
            "width": "64",
            "height": "64",
            "u_offset": 0.0,
            "v_offset": 0.0,
            "u_width": 64,
            "v_height": 64,
            "texture_width": 128,
            "texture_height": 128
          }
        }
      ],
      // 可直接填原始值，会作为text读取
      "text_group": "我是文本",
      "text_group": [
        {
          // 必须
          "text": "我是文本",
          "text_config": {
            "line_width": 200,
            "y": "@screenheight/2"
          }
        },
      ],
      // 可直接填原始值，会作为sound读取
      "sound_group": "minecraft:entity.villager.yes",
      "sound_group": {
        "sound": "minecraft:entity.villager.yes",
        "volume": 1.0,
        "pitch": 1.0
      },
      // text_effect目前未完成
      "text_effect": {
        "name": "typewriter",
        "params": {
          "speed": 1.0
        }
      }
    }
  ],
  // 必须
  "dialog_action_datas": [
    {
      "message": "第一个选项",
      "actions": [
        {
          "name": "dialog",
          "params": {
            "dialog_id": "foo:intro_2"
          }
        }
      ]
    },
    {
      "message": "第二个选项",
      "actions": [
        {
          "name": "close"
        }
      ]
    }
  ],
  "dialog_config": {
    "pause_screen": false,
    "background_config": {
      "x": 0,
      "y": "@screenheight/3*2",
      "width": "@screenwidth",
      "height": "@screenheight",
      "color_from": -1073741824,
      "color_to": -1073741824
    },
    "title_config": {
      "x": 10,
      "y": "(@screenheight/3*2)+4",
      "color": -1,
      "use_underline": true,
      "underline_config": {
        "min_x": 8,
        "min_y": "@screenheight/3*2+14",
        "max_x": "@textwidth+12",
        "max_y": "@screenheight/3*2+15",
        "color": -1
      }
    },
    "text_configs": [
      {
        "x": 20,
        "y": "(@screenheight/3*2)+29+@index*9",
        "line_width": "@screenwidth-40",
        "color": -1
      }
    ],
    "image_configs": [
      {
        "x": 0,
        "y": "(@screenheight/3*2)-64",
        "width": 64,
        "height": 64,
        "u_offset": 0,
        "v_offset": 0,
        "u_width": 64,
        "v_height": 64,
        "texture_width": 64,
        "texture_height": 64
      }
    ],
    "action_button_configs": [
      {
        "x": "@screenwidth-100",
        "y": "@screenheight/3*2-(@index+1)*30",
        "width": 100,
        "height": 20
      }
    ],
    "flip_button_config": {
      "sprites_config": {
        "enabled": "widget/cross_button",
        "enabled_focused": "widget/cross_button_highlighted"
      },
      "x": "@screenwidth-40",
      "y": "@screenheight-40",
      "width": 20,
      "height": 20
    }
  }
}
```

#### image_place_type的可选值
    FRONT  
    AFTER_BACKGROUND  
    NONE  
    AFTER_TITLE  
    AFTER_TEXT  
    AFTER_BUTTON  
    LAST  

#### 模组中自带的actions值
* close 关闭屏幕，无参数
* command 执行指令，参数名称 command
* dialog 打开对话，参数名称 dialog_id
* quest 未完成

### 对话配置文件名称内容
只会读取名为dialog_config的文件  
json中的名称与上文的dialog_config中的名称一致  
当对话文件中的dialog_config，未定义则读取配置文件的值  
而不同空间命名中的配置文件会按照加载优先级进行覆盖