(function() {
    $(document).ready(function () {
        $('#current-projects-table').dataTable({
            paginate: false,
            ordering: true,
            responsive: true,
            info:     false,
            "order": [[ 1, "desc" ]],
            columnDefs: [
                { targets: 1, type: 'de_datetime' },
                { targets: [5, 6, 7], sortable: false }
            ]
        });
        $('.btn-delete-project').on('click', deleteProject);
    });

    function deleteProject(){
        $('#deleteDialog').modal('show');
        var id = $(this).data('id');
        $('#deleteDialog .btn-primary').off('click').on('click', function(e) {
            $.ajax({
                url: serverRoot + '/prj/edit/' + id,
                type: 'DELETE',
                contentType: "application/json; charset=utf-8",
                data: JSON.stringify({id: id}),
                success: success
            });
        });
    }

    function success() {
        window.location = serverRoot + '/prj/edit';
    }
}());
