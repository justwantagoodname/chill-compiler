// 注册组件 llvm
components_register.push(layout => {
    layout.registerComponent('llvm', function (container, state) {
        const $container = $('<div>').css({
            height: '100%',
            width: '100%'
        }).addClass('llvm-editor');
        
        container.getElement().append($container);
        
        // 初始化Monaco编辑器
        require(['vs/editor/editor.main'], function() {
            const editor = monaco.editor.create($container[0], {
                value: '//等待……',
                language: 'llvm',
                theme: 'vs-dark',
                automaticLayout: true,
                readOnly: true,
            });

            // 窗口大小变化时自适应
            container.on('resize', () => {
                editor.layout();
            });
        });

        
    })
})