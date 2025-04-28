var $layout;
$(document).ready(function () {
    // 配置布局
    const config = {
        content: [{
            type: 'row',
            content: [{
                    type: 'component',
                    componentName: 'sysY',
                    title: 'sysY Editor',
                },
                {
                    type: 'component',
                    componentName: 'llvm',
                    title: 'llvm show',
                }
            ]
        }]
    };

    // 创建布局实例
    $layout = new GoldenLayout(config, '#layout-container');

    // 从component中注册组件
    registerComponents($layout)

    // 初始化布局
    $layout.init();
});

var components_register = []

async function registerComponents(layout) {
    components_register.forEach(component => {
        component(layout)
    })
}