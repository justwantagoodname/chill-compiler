// 注册组件 sysY
components_register.push(layout => {
    layout.registerComponent('sysY', function (container, state) {
        const $container = $('<div>').css({
            height: '100%',
            width: '100%'
        }).addClass('sysy-editor');

        container.getElement().append($container);

        // 初始化Monaco编辑器
        require(['vs/editor/editor.main'], function () {
            const editor = monaco.editor.create($container[0], {
                value: '//请输入sysY代码……',
                language: 'c',
                theme: 'vs-dark',
                automaticLayout: true,
                minimap: {
                    enabled: true
                }
            });

            // 内容变化监听（带防抖）
            const saveContent = debounce(async function () {
                try {
                    const response = await fetch('/api/sysycompiler', {
                        method: 'POST',
                        headers: {
                            'Content-Type': 'application/json',
                        },
                        body: JSON.stringify({
                            content: editor.getValue()
                        })
                    });

                    if (!response.ok) throw new Error('保存失败');
                    console.log('内容保存成功');
                } catch (error) {
                    console.error('保存错误:', error);
                    // 可以添加UI错误提示
                }
            }, 1000); // 1秒防抖

            editor.getModel().onDidChangeContent(saveContent);

            // 窗口大小变化时自适应
            container.on('resize', () => {
                editor.layout();
            });
        });
    });


    // 防抖函数
    function debounce(func, delay) {
        let timeout;
        return function (...args) {
            clearTimeout(timeout);
            timeout = setTimeout(() => func.apply(this, args), delay);
        };
    }
})