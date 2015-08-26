(function(){
    $( document ).ready(function() {
        var finish = function() {
            window.location = serverRoot + '/';
        };
        var shutdown = function () {
            $('#shutdownDialog').modal().on('hidden.bs.modal', function (e) {
                window.location = serverRoot + '/';
            });
            $('#shutdownDialog .btn-primary').on('click', function(e) {
                try {
                    $.ajax({
                        url: serverRoot + "/system/stop",
                        type: 'POST',
                        error: finish,
                        success: finish
                    });
                }
                catch (e) {
                    finish()
                }
            });
        };
        $('#shutdown-btn').on('click', shutdown);
    });
}());
